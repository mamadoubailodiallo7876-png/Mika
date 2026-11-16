package com.wangzan101.mika.core.career

import java.net.URI
import java.security.MessageDigest
import java.time.YearMonth

data class DuplicateMatch(val existing: JobPosting, val reason: String)

/**
 * Deduplication and listing safety. Both are deterministic and explain themselves, so a user can
 * see why a listing was treated as a duplicate or flagged.
 */
object JobDedupe {
    private val KEEP_PARAMS = setOf("id", "jobid", "job_id", "gh_jid", "currentjobid", "jk", "vjk", "job")
    private val TRACKING = Regex("(?i)^(utm_.*|gclid|fbclid|ref|referrer|source|src|trk|trackingid|lever-source|gh_src|recruitee-source)$")
    private val SHORTENERS = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly")

    fun normalizeUrl(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text)) text else "https://$text"
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return text.lowercase()
            val port = uri.port.takeIf { it > 0 && it != 443 && it != 80 }?.let { ":$it" }.orEmpty()
            val path = (uri.path ?: "").trimEnd('/').ifEmpty { "/" }
            val query = uri.rawQuery.orEmpty().split('&').filter { part ->
                part.isNotBlank() && !TRACKING.matches(part.substringBefore('=')) &&
                    (part.substringBefore('=').lowercase() in KEEP_PARAMS)
            }.sorted().joinToString("&")
            val fragment = uri.fragment?.takeIf { it.startsWith("/") }.orEmpty()
            "https://$host$port$path" + (if (query.isNotEmpty()) "?$query" else "") + fragment
        }.getOrElse { text.lowercase() }
    }

    fun fingerprint(job: JobPosting): String {
        val key = listOf(job.title, job.company, job.location).joinToString("|") {
            it.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        } + "|" + if (job.company.isBlank()) normalizeUrl(job.url) else ""
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun findDuplicate(candidate: JobPosting, existing: List<JobPosting>): DuplicateMatch? {
        val url = normalizeUrl(candidate.url)
        val appUrl = normalizeUrl(candidate.applicationUrl)
        existing.firstOrNull { it.id != candidate.id && it.fingerprint.isNotEmpty() && it.fingerprint == candidate.fingerprint }
            ?.let { return DuplicateMatch(it, "same title, company and location") }
        if (url.isNotEmpty()) {
            existing.firstOrNull { normalizeUrl(it.url) == url }?.let { return DuplicateMatch(it, "same original listing URL") }
        }
        if (appUrl.isNotEmpty()) {
            existing.firstOrNull { normalizeUrl(it.applicationUrl) == appUrl }?.let {
                return DuplicateMatch(it, "same application URL")
            }
        }
        return null
    }

    /** A duplicate can still carry new facts; merge conservatively instead of losing them. */
    fun merge(existing: JobPosting, incoming: JobPosting): JobPosting = existing.copy(
        description = existing.description.ifBlank { incoming.description },
        salaryText = existing.salaryText.ifBlank { incoming.salaryText },
        deadline = existing.deadline.ifBlank { incoming.deadline },
        requirements = (existing.requirements + incoming.requirements).distinct(),
        skills = (existing.skills + incoming.skills).distinct(),
        suspicious = (existing.suspicious + incoming.suspicious).distinct(),
        notes = listOf(existing.notes, incoming.notes).filter { it.isNotBlank() }.distinct().joinToString("\n"),
    )

    /** Safety flags for the user to review. A flag never rejects a listing on its own. */
    fun suspiciousFlags(job: JobPosting): List<String> {
        val text = "${job.title}\n${job.description}\n${job.requirements.joinToString("\n")}\n${job.salaryText}".lowercase()
        val flags = mutableListOf<String>()
        val fee = listOf("registration fee", "processing fee", "training fee", "application fee", "security deposit", "pay to apply", "refundable deposit")
        fee.firstOrNull(text::contains)?.let { flags += "The listing mentions a fee (\"$it\"). Legitimate employers do not charge candidates." }
        listOf("bank account number", "routing number", "send your card", "credit card details", "crypto wallet").firstOrNull(text::contains)
            ?.let { flags += "The listing asks for financial details (\"$it\")." }
        if (job.company.isBlank()) flags += "No employer name was provided."
        if (text.contains("unpaid") && (text.contains("intern") || text.contains("internship"))) flags += "This appears to be an unpaid internship."
        if ((text.contains("whatsapp") || text.contains("telegram")) && job.company.isBlank()) {
            flags += "Contact is only through a messaging app and no employer is named."
        }
        val host = runCatching { URI(if (job.url.startsWith("http")) job.url else "https://${job.url}").host?.lowercase() }.getOrNull()
        if (host != null && host.removePrefix("www.") in SHORTENERS) flags += "The link is a URL shortener, so the destination is hidden."
        val hourly = Regex("(?i)(?:usd|\\$|€|£)\\s?\\d{3,}\\s*(?:per|/)\\s*(?:hour|hr)").containsMatchIn(text)
        if (hourly) flags += "The advertised hourly rate is unusually high; verify the employer independently."
        if (Regex("(?i)no experience (?:required|needed)").containsMatchIn(text) && Regex("(?i)(?:usd|\\$|€|£)\\s?\\d{1,3},\\d{3}").containsMatchIn(text)) {
            flags += "A high salary with no experience required is a common scam pattern."
        }
        if (job.description.length in 1..80 && job.requirements.isEmpty()) flags += "The listing has almost no detail."
        return flags.distinct()
    }

    fun refreshStatuses(jobs: List<JobPosting>, today: YearMonth = YearMonth.now()): List<JobPosting> = jobs.map { job ->
        if (job.status == JobStatus.Active && CareerDates.isExpired(job.deadline, today)) job.copy(status = JobStatus.Expired) else job
    }
}

data class ExtractedJob(val job: JobPosting, val uncertain: List<String>)

/**
 * Extracts a structured listing from text the user copied or an agent read. It only records what
 * is present. Untrusted text is data here: nothing in it becomes an instruction.
 */
object JobExtractor {
    private val HEADING = Regex("^[A-Z][A-Za-z /&()-]{2,48}:?$")
    private val BULLET = Regex("^\\s*(?:[-*•·]|\\d+[.)])\\s+(.+)$")
    private val COMPANY = Regex("(?im)^(?:company|employer|organisation|organization)\\s*:\\s*(.+)$")
    private val LOCATION = Regex("(?im)^(?:location|based in|work location)\\s*:\\s*(.+)$")
    private val SALARY = Regex("(?im)^(?:salary|compensation|pay|package)\\s*:\\s*(.+)$")
    private val DEADLINE = Regex("(?im)^(?:deadline|closes|closing date|apply by)\\s*:\\s*((?:19|20)\\d{2}-\\d{2}-\\d{2})")
    private val YEARS = Regex("(?i)(\\d{1,2})\\s*\\+?\\s*years?")
    private val REMOTE = Regex("(?i)\\bremote\\b|\\bwork from home\\b|\\banywhere\\b")
    private val HYBRID = Regex("(?i)\\bhybrid\\b")
    private val ONSITE = Regex("(?i)\\bon[- ]?site\\b|\\bin office\\b")

    fun fromText(
        text: String,
        id: String,
        nowMs: Long,
        url: String = "",
        source: String = "",
        titleHint: String = "",
        companyHint: String = "",
    ): ExtractedJob {
        val cleaned = text.replace("\r\n", "\n").replace('\u0000', ' ').trim()
        val lines = cleaned.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val uncertain = mutableListOf<String>()

        fun section(vararg names: String): List<String> {
            val start = lines.indexOfFirst { line ->
                val h = line.lowercase().trim().trimEnd(':')
                names.any { h == it || h.startsWith("$it ") }
            }
            if (start < 0) return emptyList()
            val out = mutableListOf<String>()
            for (line in lines.drop(start + 1)) {
                if (HEADING.matches(line.trim()) && line.trim().length < 50 && BULLET.find(line) == null) break
                out += line
            }
            return out
        }

        val metaLine = Regex("(?i)^(company|employer|location|salary|compensation|pay|deadline|closes|apply by|source)\\s*:")
        val firstLine = lines.firstOrNull { !metaLine.containsMatchIn(it) }.orEmpty()
        val explicitTitle = lines.firstOrNull { it.lowercase().startsWith("job title:") }
            ?.substringAfter(':')?.trim()
        val title = listOf(titleHint, explicitTitle, firstLine)
            .firstOrNull { it?.isNotBlank() == true }?.removePrefix("#")?.trim()?.take(160).orEmpty()
        if (title.isBlank()) uncertain += "job title"
        if (title.length > 120) uncertain += "job title is unusually long"

        val company = listOf(
            companyHint,
            COMPANY.find(cleaned)?.groupValues?.get(1)?.trim().orEmpty(),
            Regex("(?i)\\bat\\s+([A-Z][A-Za-z0-9&.,' -]{2,60})").find(lines.take(6).joinToString("\n"))
                ?.groupValues?.get(1)?.trim().orEmpty(),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        if (company.isBlank()) uncertain += "employer name"

        val location = LOCATION.find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        if (location.isBlank()) uncertain += "location"

        val salary = SALARY.find(cleaned)?.groupValues?.get(1)?.trim().orEmpty()
        val deadline = DEADLINE.find(cleaned)?.groupValues?.get(1) ?: ""

        val requirementLines = section("requirements", "qualifications", "what you'll need", "what we're looking for", "about you")
            .mapNotNull { BULLET.find(it)?.groupValues?.get(1) ?: it.takeIf { l -> l.length in 12..300 } }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(20)
        val requirements = if (requirementLines.isNotEmpty()) requirementLines else lines
            .filter { Regex("(?i)experience with|knowledge of|proficien|degree|years? of").containsMatchIn(it) }
            .map { it.trim() }.take(12)

        val skills = SkillVocabulary.extract(
            (requirements + section("skills", "tech stack", "technologies")).joinToString("\n") + "\n" + cleaned,
        ).take(20)

        val years = YEARS.findAll(cleaned).mapNotNull { it.groupValues[1].toIntOrNull() }.filter { it in 0..40 }.minOrNull()
        val employment = when {
            Regex("(?i)\\bintern(?:ship)?\\b").containsMatchIn(cleaned) -> EmploymentType.Internship
            Regex("(?i)\\bpart[- ]time\\b").containsMatchIn(cleaned) -> EmploymentType.PartTime
            Regex("(?i)\\bcontract(?:or)?\\b|\\bfreelance\\b").containsMatchIn(cleaned) -> EmploymentType.Contract
            Regex("(?i)\\btemporary\\b|\\bfixed[- ]term\\b").containsMatchIn(cleaned) -> EmploymentType.Temporary
            Regex("(?i)\\bvolunteer\\b").containsMatchIn(cleaned) -> EmploymentType.Volunteer
            Regex("(?i)\\bfull[- ]time\\b").containsMatchIn(cleaned) -> EmploymentType.FullTime
            else -> EmploymentType.Unspecified
        }
        val remote = when {
            HYBRID.containsMatchIn(cleaned.take(1200)) -> RemotePreference.Hybrid
            REMOTE.containsMatchIn(cleaned.take(1200)) -> RemotePreference.Remote
            ONSITE.containsMatchIn(cleaned.take(1200)) -> RemotePreference.OnSite
            else -> RemotePreference.Unspecified
        }
        val education = requirements.firstOrNull {
            Regex("(?i)\\b(degree|bachelor|master|phd|diploma|high school)\\b").containsMatchIn(it)
        }.orEmpty()

        val resolvedSource = source.ifBlank {
            runCatching { URI(if (url.startsWith("http")) url else "https://$url").host?.removePrefix("www.") }.getOrNull().orEmpty()
        }
        val draft = JobPosting(
            id = id,
            title = title,
            company = company,
            description = cleaned.take(20_000),
            location = location,
            remote = remote,
            salaryText = salary,
            employmentType = employment,
            requirements = requirements,
            skills = skills,
            experienceYears = years,
            educationRequirement = education,
            deadline = deadline,
            source = resolvedSource,
            url = url.trim(),
            applicationUrl = url.trim(),
            discoveredAtMs = nowMs,
            status = JobStatus.Active,
        )
        val complete = draft.copy(fingerprint = JobDedupe.fingerprint(draft), suspicious = JobDedupe.suspiciousFlags(draft))
        if (salary.isBlank()) uncertain += "salary (not stated)"
        if (deadline.isBlank()) uncertain += "application deadline (not stated)"
        if (requirements.isEmpty()) uncertain += "requirements"
        return ExtractedJob(complete, uncertain.distinct())
    }

    /** Build from fields the agent already extracted, still applying dedupe and safety checks. */
    fun fromFields(
        id: String,
        nowMs: Long,
        title: String,
        company: String,
        description: String,
        location: String,
        salaryText: String,
        employmentType: EmploymentType,
        requirements: List<String>,
        skills: List<String>,
        experienceYears: Int?,
        deadline: String,
        source: String,
        url: String,
        remote: RemotePreference = RemotePreference.Unspecified,
    ): ExtractedJob {
        val base = JobPosting(
            id = id,
            title = Validators.bounded(title, 160),
            company = Validators.bounded(company, 160),
            description = Validators.bounded(description, 20_000),
            location = Validators.bounded(location, 160),
            remote = remote,
            salaryText = Validators.bounded(salaryText, 120),
            employmentType = employmentType,
            requirements = requirements.map { Validators.bounded(it, 300) }.filter { it.isNotBlank() }.take(20),
            skills = (skills + SkillVocabulary.extract(description)).map { Validators.bounded(it, 60) }
                .filter { it.isNotBlank() }.distinct().take(30),
            experienceYears = experienceYears?.takeIf { it in 0..60 },
            deadline = Validators.bounded(deadline, 20),
            source = Validators.bounded(source, 120),
            url = Validators.bounded(url, 2000),
            applicationUrl = Validators.bounded(url, 2000),
            discoveredAtMs = nowMs,
        ).let { if (it.remote == RemotePreference.Unspecified) it.copy(remote = inferRemote(location, description)) else it }
        val complete = base.copy(fingerprint = JobDedupe.fingerprint(base), suspicious = JobDedupe.suspiciousFlags(base))
        val uncertain = buildList {
            if (complete.company.isBlank()) add("employer name")
            if (complete.description.isBlank()) add("description")
            if (complete.location.isBlank()) add("location")
            if (complete.url.isBlank()) add("original listing URL")
        }
        return ExtractedJob(complete, uncertain)
    }

    private fun inferRemote(location: String, description: String): RemotePreference {
        val text = "${location.take(120)}\n${description.take(400)}"
        return when {
            HYBRID.containsMatchIn(text) -> RemotePreference.Hybrid
            REMOTE.containsMatchIn(text) -> RemotePreference.Remote
            ONSITE.containsMatchIn(text) -> RemotePreference.OnSite
            else -> RemotePreference.Unspecified
        }
    }
}

object JobSearch {
    /** Local search over listings already captured by the user, newest first. */
    fun search(jobs: List<JobPosting>, query: String, includeClosed: Boolean = false): List<JobPosting> {
        val tokens = SkillVocabulary.normalize(query).split(' ').filter { it.length >= 2 }
        return jobs.asSequence()
            .filter { includeClosed || it.status in setOf(JobStatus.Active, JobStatus.Shortlisted) }
            .filter { job ->
                tokens.isEmpty() || tokens.all { token ->
                    SkillVocabulary.normalize(
                        "${job.title} ${job.company} ${job.location} ${job.skills.joinToString(" ")} ${job.description.take(1500)}",
                    ).contains(token)
                }
            }
            .sortedWith(compareByDescending<JobPosting> { it.discoveredAtMs }.thenBy { it.title })
            .toList()
    }

    fun stats(jobs: List<JobPosting>, today: YearMonth = YearMonth.now()): JobSearchStats {
        val active = jobs.count { it.status == JobStatus.Active && !CareerDates.isExpired(it.deadline, today) }
        return JobSearchStats(
            total = jobs.size,
            active = active,
            expired = jobs.count { it.status == JobStatus.Expired || CareerDates.isExpired(it.deadline, today) },
            flagged = jobs.count { it.suspicious.isNotEmpty() },
            shortlisted = jobs.count { it.status == JobStatus.Shortlisted },
        )
    }
}

data class JobSearchStats(val total: Int, val active: Int, val expired: Int, val flagged: Int, val shortlisted: Int)

