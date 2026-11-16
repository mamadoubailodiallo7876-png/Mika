package com.wangzan101.mika.core.career

import java.time.YearMonth

data class ExtractedField(val field: String, val value: String, val evidence: String, val confidence: Double)

data class ProfileImportResult(
    val suggestions: CandidateProfile,
    val fields: List<ExtractedField>,
    val warnings: List<String>,
    val sectionsFound: List<String>,
) {
    val proposedCount: Int get() = suggestions.proposedFactCount()
}

/**
 * Converts CV text into *proposals*. Every imported entry is marked unconfirmed and excluded from
 * generated documents until the candidate reviews it. Scalar fields are suggestions too; the UI
 * shows them before merging.
 */
object ProfileImporter {
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?:\\+?\\d[\\d ()-]{7,}\\d)")
    private val URL = Regex("(?i)\\b(?:https?://|www\\.)[^\\s)]+")
    private val DATE = "(?:(?:19|20)\\d{2}|[A-Za-z]{3,9}\\.?\\s+(?:19|20)\\d{2}|\\d{1,2}/(?:19|20)\\d{2})"
    private val RANGE = Regex("(?i)($DATE)\\s*(?:-|–|—|to|until)\\s*($DATE|present|current|now)")
    private val SINGLE_DATE = Regex("(?i)($DATE)")
    private val HEADING_NAMES = listOf(
        "summary", "profile", "about", "objective", "experience", "employment", "work history", "professional experience",
        "education", "skills", "technical skills", "core skills", "certifications", "certificates", "qualifications",
        "projects", "languages", "awards", "publications", "volunteering", "references", "contact",
    )

    fun fromText(text: String, fileName: String, ids: (String) -> String, nowMs: Long, now: YearMonth = YearMonth.now()): ProfileImportResult {
        val cleaned = text.replace("\r\n", "\n").replace('\u0000', ' ').replace(Regex("[ \\t]+"), " ").trim()
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val warnings = mutableListOf<String>()
        val fields = mutableListOf<ExtractedField>()
        val sections = linkedSetOf<String>()

        if (cleaned.isBlank()) {
            return ProfileImportResult(CandidateProfile(updatedAtMs = nowMs), emptyList(), listOf("The file contained no readable text."), emptyList())
        }

        fun firstMatch(regex: Regex): String? = regex.find(cleaned)?.value?.trim()

        val email = firstMatch(EMAIL).orEmpty()
        val phone = firstMatch(PHONE).orEmpty()
        val urls = URL.findAll(cleaned).map { it.value.trimEnd('.', ',', ')', ';') }.distinct().toList()
        var linkedin = urls.firstOrNull { it.contains("linkedin.com", ignoreCase = true) }.orEmpty()
        var github = urls.firstOrNull { it.contains("github.com", ignoreCase = true) }.orEmpty()
        val otherLink = urls.firstOrNull { it != linkedin && it != github }.orEmpty()
        if (linkedin.isBlank()) {
            Regex("(?i)\\blinkedin\\.com/in/[A-Za-z0-9_-]+").find(cleaned)?.value?.let { linkedin = "https://$it" }
        }
        if (github.isBlank()) {
            Regex("(?i)\\bgithub\\.com/[A-Za-z0-9_-]+").find(cleaned)?.value?.let { github = "https://$it" }
        }

        val headingLines = lines.filter(::isHeading)
        headingLines.forEach { h -> HEADING_NAMES.firstOrNull { h.lowercase().trimEnd(':') == it }?.let { sections += it } }

        val nameLine = lines.firstOrNull { line ->
            line.length in 2..80 && !EMAIL.containsMatchIn(line) && !PHONE.containsMatchIn(line) &&
                URL.find(line) == null && !isHeading(line) && line.split(' ').size in 1..8 &&
                !Regex("(?i)^(curriculum vitae|resume|cv|profile|contact)\\s*:?$").matches(line)
        }.orEmpty()
        val locationLine = Regex("(?im)^(?:location|based in)\\s*:\\s*(.+)$").find(cleaned)?.groupValues?.get(1)?.trim()
            ?: lines.take(8).firstOrNull { Regex("^[A-Z][A-Za-z .'-]{1,40},\\s*[A-Z][A-Za-z .'-]{1,40}$").matches(it) }.orEmpty()
        val headline = lines.dropWhile { it != nameLine }.drop(1).firstOrNull { line ->
            line.length in 8..120 && !EMAIL.containsMatchIn(line) && !PHONE.containsMatchIn(line) &&
                URL.find(line) == null && !isHeading(line) && RANGE.find(line) == null
        }.orEmpty()

        if (nameLine.isNotBlank()) fields += ExtractedField("fullName", nameLine, nameLine, 0.8)
        if (email.isNotBlank()) fields += ExtractedField("email", email, email, 0.95)
        if (phone.isNotBlank()) fields += ExtractedField("phone", phone, phone, 0.8)
        if (locationLine.isNotBlank()) fields += ExtractedField("location", locationLine, locationLine, 0.55)
        if (linkedin.isNotBlank()) fields += ExtractedField("links.linkedin", linkedin, linkedin, 0.9)
        if (github.isNotBlank()) fields += ExtractedField("links.github", github, github, 0.9)

        fun section(vararg names: String): List<String> {
            val start = lines.indexOfFirst { line -> isHeading(line) && names.any { it == line.lowercase().trimEnd(':') } }
            if (start < 0) return emptyList()
            val out = mutableListOf<String>()
            for (line in lines.drop(start + 1)) {
                if (isHeading(line)) break
                out += line
            }
            return out
        }

        val summary = section("summary", "profile", "about", "objective").joinToString(" ").take(1200).trim()
        if (summary.isNotBlank()) fields += ExtractedField("summary", summary, "Summary section", 0.75)

        val skillLines = section("skills", "technical skills", "core skills")
        val skillSource = if (skillLines.isNotEmpty()) skillLines else lines.filter { Regex("(?im)^skills?\\s*:").containsMatchIn(it) }
        val skillNames = skillSource.flatMap { SkillVocabulary.splitList(it.replace(Regex("(?i)^skills?\\s*:"), "")) }
            .map { it.removePrefix("-").trim() }
            .filter { it.length in 1..60 }
            .distinctBy { SkillVocabulary.canonical(it) }
            .take(40)
        if (skillNames.isNotEmpty()) fields += ExtractedField("skills", skillNames.joinToString(", "), "Skills section", 0.65)

        val experienceLines = section("experience", "employment", "work history", "professional experience")
        val experience = mutableListOf<ExperienceEntry>()
        var i = 0
        while (i < experienceLines.size && experience.size < 15) {
            val line = experienceLines[i]
            val range = RANGE.find(line)
            if (range == null) { i++; continue }
            val start = range.groupValues[1]
            val endRaw = range.groupValues[2]
            val current = endRaw.lowercase() in setOf("present", "current", "now")
            val end = if (current) "" else endRaw
            val withoutDates = line.replace(range.value, "").replace(Regex("\\s*\\(\\)\\s*"), "").trim().trim('-', '—', '–', '|', ',').trim()
            val previous = experienceLines.getOrNull(i - 1)?.takeIf { !RANGE.containsMatchIn(it) && it.length <= 120 }
            val beforePrevious = experienceLines.getOrNull(i - 2)?.takeIf { !RANGE.containsMatchIn(it) && it.length <= 120 }
            val (title, company) = splitRole(withoutDates, previous, beforePrevious)
            if (title.isBlank() && company.isBlank()) { i++; continue }
            val bullets = mutableListOf<String>()
            var j = i + 1
            while (j < experienceLines.size && RANGE.find(experienceLines[j]) == null) {
                val next = experienceLines[j]
                // The title/company lines of the next role do not carry dates; stop before them.
                val nextIsRoleHeader = !BULLET.containsMatchIn(next) && j + 1 < experienceLines.size &&
                    RANGE.containsMatchIn(experienceLines[j + 1])
                if (nextIsRoleHeader) break
                val b = next.removePrefix("-").removePrefix("•").removePrefix("*").trim()
                if (b.isNotBlank()) bullets += b
                j++
            }
            if (bullets.isEmpty()) warnings += "A role ($title) was found without achievement bullets."
            experience += ExperienceEntry(
                id = ids("exp"), company = company, title = title,
                start = start, end = end, current = current,
                summary = "",
                bullets = bullets.take(6),
                source = FactSource.ImportedDocument, confirmed = false,
            )
            i = maxOf(i + 1, j)
        }

        val educationLines = section("education")
        val education = educationLines.mapIndexedNotNull { index, line ->
            if (RANGE.find(line) == null && line.length < 8) return@mapIndexedNotNull null
            val institutionKeyword = Regex("(?i)\\b(university|college|institute|school|academy|polytechnic)\\b")
            if (!institutionKeyword.containsMatchIn(line) && ',' !in line && " - " !in line) {
                warnings += "An education line was skipped because no institution could be identified: ${line.take(80)}"
                return@mapIndexedNotNull null
            }
            val range = RANGE.find(line)
            val withoutDates = range?.let { line.replace(it.value, "") }?.replace(Regex("\\s*\\(\\)\\s*"), "")?.trim() ?: line
            val parts = withoutDates.split(" — ", " – ", " - ", "|", ",").map { it.trim() }.filter { it.isNotBlank() }
            val institution = parts.firstOrNull { part ->
                Regex("(?i)\\b(university|college|institute|school|academy|polytechnic)\\b").containsMatchIn(part)
            } ?: parts.firstOrNull().orEmpty()
            val degreeWord = Regex("(?i)\\b(bachelor|master|phd|doctor|b\\.?sc|m\\.?sc|mba|diploma|certificate|associate)\\b")
            val degree = parts.firstOrNull { part -> degreeWord.containsMatchIn(part) }
                ?: educationLines.getOrNull(index - 1)?.takeIf { degreeWord.containsMatchIn(it) }?.trim()
                ?: parts.getOrNull(1).orEmpty()
            if (institution.isBlank()) return@mapIndexedNotNull null
            EducationEntry(
                id = ids("edu"), institution = institution, degree = degree,
                start = range?.groupValues?.get(1).orEmpty(),
                end = range?.let { r -> r.groupValues[2].takeUnless { it.lowercase() in setOf("present", "current", "now") }.orEmpty() }.orEmpty(),
                source = FactSource.ImportedDocument, confirmed = false,
            )
        }

        val certificationLines = section("certifications", "certificates", "qualifications")
        val certifications = certificationLines
            .filter { it.length in 3..140 && !isHeading(it) }
            .take(12)
            .map { line ->
                val year = SINGLE_DATE.find(line)?.value
                CertificationEntry(
                    id = ids("cert"),
                    name = line.replace(year.orEmpty(), "").replace(Regex("\\s*\\(\\)\\s*"), "").trim().trim('-', '—', '·').trim(),
                    year = year.orEmpty(), source = FactSource.ImportedDocument, confirmed = false,
                )
            }
            .filter { it.name.isNotBlank() }

        val projectLines = section("projects")
        val projects = mutableListOf<ProjectEntry>()
        for (line in projectLines.take(60)) {
            if (line.isBlank() || isHeading(line)) continue
            if (BULLET.containsMatchIn(line) && projects.isNotEmpty()) {
                val last = projects.removeAt(projects.lastIndex)
                projects += last.copy(bullets = (last.bullets + line.removePrefix("-").removePrefix("•").trim()).take(5))
            } else if (projects.size < 8) {
                projects += ProjectEntry(
                    id = ids("proj"), name = line.removePrefix("-").trim().take(120),
                    source = FactSource.ImportedDocument, confirmed = false,
                )
            }
        }

        val languageLines = section("languages")
        val languages = languageLines.flatMap { SkillVocabulary.splitList(it) }
            .filter { it.length in 2..40 }
            .distinct()
            .take(10)
            .map { LanguageEntry(id = ids("lang"), name = it, source = FactSource.ImportedDocument, confirmed = false) }

        val suggestions = CandidateProfile(
            fullName = nameLine,
            headline = headline,
            summary = summary,
            email = email,
            phone = phone,
            location = locationLine,
            links = ProfileLinks(github = github, linkedin = linkedin, portfolio = if (otherLink != linkedin && otherLink != github) otherLink else ""),
            education = education,
            experience = experience,
            skills = skillNames.map { SkillEntry(id = ids("skill"), name = it, source = FactSource.ImportedDocument, confirmed = false) },
            certifications = certifications,
            projects = projects,
            languages = languages,
            updatedAtMs = nowMs,
        )

        if (experience.isEmpty() && "experience" in sections) warnings += "An experience section was found, but no dated roles could be read."
        if (skillNames.isEmpty() && "skills" in sections) warnings += "A skills section was found, but no skills could be read."
        if (education.isEmpty() && "education" in sections) warnings += "An education section was found, but no entries could be read."
        warnings += "Imported facts stay unconfirmed until you review them; documents only use confirmed facts."
        if (fileName.endsWith(".pdf", ignoreCase = true) && cleaned.length < 200) {
            warnings += "Very little text came out of this PDF. If it is a scan, paste the text or use a text-based CV."
        }
        return ProfileImportResult(
            suggestions = suggestions.copy(
                experience = experience.sortedByDescending { CareerDates.parse(it.start) ?: YearMonth.of(1900, 1) },
            ),
            fields = fields,
            warnings = warnings.distinct(),
            sectionsFound = sections.toList(),
        )
    }

    /** Merge proposals into an existing profile without overwriting anything the candidate typed. */
    fun merge(existing: CandidateProfile, imported: CandidateProfile, nowMs: Long): CandidateProfile {
        fun <T> combine(a: List<T>, b: List<T>, key: (T) -> String): List<T> {
            val seen = a.map { key(it) }.toMutableSet()
            return a + b.filter { seen.add(key(it)) }
        }
        return existing.copy(
            fullName = existing.fullName.ifBlank { imported.fullName },
            headline = existing.headline.ifBlank { imported.headline },
            summary = existing.summary.ifBlank { imported.summary },
            email = existing.email.ifBlank { imported.email },
            phone = existing.phone.ifBlank { imported.phone },
            location = existing.location.ifBlank { imported.location },
            links = ProfileLinks(
                github = existing.links.github.ifBlank { imported.links.github },
                linkedin = existing.links.linkedin.ifBlank { imported.links.linkedin },
                portfolio = existing.links.portfolio.ifBlank { imported.links.portfolio },
                website = existing.links.website.ifBlank { imported.links.website },
            ),
            education = combine(existing.education, imported.education) { "${it.institution}|${it.degree}".lowercase() },
            experience = combine(existing.experience, imported.experience) { "${it.company}|${it.title}|${it.start}".lowercase() },
            skills = combine(existing.skills, imported.skills) { SkillVocabulary.canonical(it.name) },
            certifications = combine(existing.certifications, imported.certifications) { it.name.lowercase() },
            projects = combine(existing.projects, imported.projects) { it.name.lowercase() },
            languages = combine(existing.languages, imported.languages) { it.name.lowercase() },
            qualifications = combine(existing.qualifications, imported.qualifications) { it.name.lowercase() },
            updatedAtMs = nowMs,
        )
    }

    private val TITLE_WORDS = Regex(
        "(?i)\\b(engineer|developer|manager|analyst|designer|consultant|director|lead|intern|specialist|scientist|architect|administrator|accountant|associate|assistant|coordinator|officer|executive|founder|researcher|teacher|nurse|technician|supervisor|head of|vp|chief)\\b",
    )

    private fun splitRole(header: String, previous: String?, beforePrevious: String?): Pair<String, String> {
        val clean = header.trim().trim('-', '—', '–', '|').trim()
        Regex("(?i)^(.+?)\\s+at\\s+(.+)$").matchEntire(clean)?.let { return it.groupValues[1].trim() to it.groupValues[2].trim() }
        val parts = clean.split(" — ", " – ", " | ", " - ").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size >= 2) {
            // Common CV layout: "Company — Title" or "Title — Company". Prefer the side that names a role.
            return if (TITLE_WORDS.containsMatchIn(parts[0]) || !TITLE_WORDS.containsMatchIn(parts[1])) {
                parts[0] to parts[1]
            } else {
                parts[1] to parts[0]
            }
        }
        if (parts.size == 1) {
            val headerText = parts[0]
            if (previous != null && TITLE_WORDS.containsMatchIn(previous)) return previous to headerText
            if (previous != null) return headerText to previous
            if (beforePrevious != null && TITLE_WORDS.containsMatchIn(beforePrevious)) return beforePrevious to headerText
            return headerText to ""
        }
        return "" to ""
    }

    private fun isHeading(line: String): Boolean {
        val t = line.trim().trimEnd(':')
        if (t.isEmpty() || t.length > 40 || BULLET.containsMatchIn(line)) return false
        val lower = t.lowercase()
        if (lower !in HEADING_NAMES) return false
        return true
    }

    private val BULLET = Regex("^\\s*(?:[-*•·]|\\d+[.)])\\s+")
}
