package com.wangzan101.mika.core.career

import java.time.YearMonth
import kotlin.math.max
import kotlin.math.roundToInt

/** Confirmed facts only. Imported or agent-drafted proposals are not evidence of anything. */
fun CandidateProfile.confirmedExperience(): List<ExperienceEntry> = experience.filter { it.confirmed }

fun CandidateProfile.confirmedEducation(): List<EducationEntry> = education.filter { it.confirmed }

fun CandidateProfile.confirmedSkills(): List<SkillEntry> = skills.filter { it.confirmed }

fun CandidateProfile.confirmedProjects(): List<ProjectEntry> = projects.filter { it.confirmed }

fun CandidateProfile.confirmedCertifications(): List<CertificationEntry> = certifications.filter { it.confirmed }

fun CandidateProfile.confirmedQualifications(): List<QualificationEntry> = qualifications.filter { it.confirmed }

fun CandidateProfile.confirmedLanguages(): List<LanguageEntry> = languages.filter { it.confirmed }

fun CandidateProfile.proposedFactCount(): Int =
    experience.count { !it.confirmed } + education.count { !it.confirmed } + skills.count { !it.confirmed } +
        certifications.count { !it.confirmed } + projects.count { !it.confirmed } + languages.count { !it.confirmed } +
        qualifications.count { !it.confirmed }

/** Lenient date parsing for the free-form dates people write on CVs. */
object CareerDates {
    private val MONTHS = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )
    private val YEAR = Regex("(?:19|20)\\d{2}")
    private val MONTH_YEAR = Regex("^([A-Za-z]{3,9})\\.?\\s+((?:19|20)\\d{2})$")
    private val NUMERIC = Regex("^(\\d{1,2})[/-]((?:19|20)\\d{2})$")

    fun parse(text: String): YearMonth? {
        val s = text.trim()
        if (s.isEmpty()) return null
        YEAR.matchEntire(s)?.let { return YearMonth.of(it.value.toInt(), 1) }
        Regex("^((?:19|20)\\d{2})-(\\d{2})$").matchEntire(s)?.let { m ->
            val month = m.groupValues[2].toInt()
            if (month in 1..12) return YearMonth.of(m.groupValues[1].toInt(), month)
        }
        NUMERIC.matchEntire(s)?.let { m ->
            val month = m.groupValues[1].toInt()
            if (month in 1..12) return YearMonth.of(m.groupValues[2].toInt(), month)
        }
        MONTH_YEAR.matchEntire(s)?.let { m ->
            val month = MONTHS[m.groupValues[1].lowercase()] ?: return null
            return YearMonth.of(m.groupValues[2].toInt(), month)
        }
        return null
    }

    /** Completed months covered by confirmed employment, merging overlapping roles. */
    fun experienceMonths(profile: CandidateProfile, now: YearMonth = YearMonth.now()): Int {
        val ranges = profile.confirmedExperience().mapNotNull { e ->
            val start = parse(e.start) ?: return@mapNotNull null
            val end = if (e.current) now else parse(e.end) ?: return@mapNotNull null
            if (end < start) null else start..end
        }
        return mergedMonths(ranges)
    }

    fun experienceYears(profile: CandidateProfile, now: YearMonth = YearMonth.now()): Double =
        experienceMonths(profile, now) / 12.0

    private fun mergedMonths(ranges: List<ClosedRange<YearMonth>>): Int {
        if (ranges.isEmpty()) return 0
        val sorted = ranges.sortedBy { it.start }
        var total = 0
        var start = sorted.first().start
        var end = sorted.first().endInclusive
        for (r in sorted.drop(1)) {
            if (r.start <= end.plusMonths(1)) {
                if (r.endInclusive > end) end = r.endInclusive
            } else {
                total += monthsBetween(start, end)
                start = r.start
                end = r.endInclusive
            }
        }
        total += monthsBetween(start, end)
        return total
    }

    private fun monthsBetween(start: YearMonth, end: YearMonth): Int =
        (end.year - start.year) * 12 + (end.monthValue - start.monthValue) + 1

    fun isExpired(deadline: String, today: YearMonth = YearMonth.now()): Boolean {
        val date = Regex("^((?:19|20)\\d{2})-(\\d{2})-(\\d{2})$").matchEntire(deadline.trim()) ?: return false
        val month = date.groupValues[2].toInt()
        if (month !in 1..12) return false
        val ym = YearMonth.of(date.groupValues[1].toInt(), month)
        // Same-month deadlines are treated as still open; timezone differences must not hide a job.
        return ym < today
    }
}

enum class IssueSeverity { Error, Warning, Info }

data class ProfileIssue(val field: String, val severity: IssueSeverity, val message: String)

data class ProfileSectionScore(
    val id: String,
    val label: String,
    val earned: Int,
    val weight: Int,
    val missing: List<String>,
) {
    val complete: Boolean get() = earned >= weight
}

data class ProfileScore(
    val percent: Int,
    val sections: List<ProfileSectionScore>,
    val missing: List<String>,
) {
    val complete: Boolean get() = percent >= 100
}

/**
 * Deterministic profile quality checks. No model is involved, so the score explains itself and
 * never rewards invented content: unconfirmed proposals earn nothing.
 */
object ProfileQuality {
    fun score(profile: CandidateProfile, now: YearMonth = YearMonth.now()): ProfileScore {
        val sections = mutableListOf<ProfileSectionScore>()
        fun section(id: String, label: String, weight: Int, parts: List<Pair<String, Boolean>>) {
            val missing = parts.filterNot { it.second }.map { it.first }
            val earned = if (parts.isEmpty()) 0 else
                ((weight.toDouble() * parts.count { it.second }) / parts.size).roundToInt()
            sections += ProfileSectionScore(id, label, earned, weight, missing)
        }
        section(
            "identity", "Identity", 12,
            listOf("full name" to profile.fullName.isNotBlank(), "headline" to profile.headline.isNotBlank(),
                "summary" to profile.summary.isNotBlank()),
        )
        section(
            "contact", "Contact details", 10,
            listOf("email" to profile.email.isNotBlank(), "phone" to profile.phone.isNotBlank(),
                "location" to profile.location.isNotBlank()),
        )
        section(
            "links", "Professional links", 4,
            listOf("LinkedIn" to profile.links.linkedin.isNotBlank(), "GitHub" to profile.links.github.isNotBlank()),
        )
        section(
            "experience", "Employment history", 24,
            listOf(
                "at least one confirmed role" to profile.confirmedExperience().isNotEmpty(),
                "role dates" to profile.confirmedExperience().any { CareerDates.parse(it.start) != null },
                "role achievements" to profile.confirmedExperience().any { it.bullets.any(String::isNotBlank) },
            ),
        )
        section(
            "education", "Education", 12,
            listOf("at least one confirmed entry" to profile.confirmedEducation().isNotEmpty()),
        )
        section(
            "skills", "Skills", 14,
            listOf("at least three confirmed skills" to (profile.confirmedSkills().size >= 3)),
        )
        section(
            "projects", "Projects", 8,
            listOf("at least one confirmed project" to profile.confirmedProjects().isNotEmpty()),
        )
        section(
            "credentials", "Certifications and qualifications", 4,
            listOf("a certification or qualification" to
                (profile.confirmedCertifications().isNotEmpty() || profile.confirmedQualifications().isNotEmpty())),
        )
        section(
            "languages", "Languages", 4,
            listOf("at least one language" to profile.confirmedLanguages().isNotEmpty()),
        )
        section(
            "preferences", "Job preferences", 8,
            listOf("target role" to profile.preferences.targetRoles.isNotEmpty(),
                "location or remote preference" to
                    (profile.preferences.preferredLocations.isNotEmpty() || profile.preferences.remote != RemotePreference.Unspecified)),
        )
        val totalWeight = sections.sumOf { it.weight }
        val earned = sections.sumOf { it.earned }
        return ProfileScore(
            percent = if (totalWeight == 0) 0 else (100.0 * earned / totalWeight).roundToInt().coerceIn(0, 100),
            sections = sections,
            missing = sections.flatMap { it.missing },
        )
    }

    fun validate(profile: CandidateProfile): List<ProfileIssue> {
        val out = mutableListOf<ProfileIssue>()
        if (profile.fullName.isBlank()) out += ProfileIssue("fullName", IssueSeverity.Warning, "Add your full name.")
        if (profile.email.isNotBlank() && !Validators.isEmail(profile.email)) {
            out += ProfileIssue("email", IssueSeverity.Error, "Email address is not valid.")
        }
        listOf(
            "links.github" to profile.links.github,
            "links.linkedin" to profile.links.linkedin,
            "links.portfolio" to profile.links.portfolio,
            "links.website" to profile.links.website,
        ).forEach { (field, value) ->
            if (value.isNotBlank() && !Validators.isWebUrl(value)) {
                out += ProfileIssue(field, IssueSeverity.Warning, "Not a valid http(s) link: $value")
            }
        }
        profile.experience.forEach { e ->
            if (e.company.isBlank()) out += ProfileIssue("experience.${e.id}.company", IssueSeverity.Error, "A role needs an employer.")
            if (e.title.isBlank()) out += ProfileIssue("experience.${e.id}.title", IssueSeverity.Error, "A role needs a job title.")
            val start = CareerDates.parse(e.start)
            val end = CareerDates.parse(e.end)
            if (start != null && end != null && end < start) {
                out += ProfileIssue("experience.${e.id}.end", IssueSeverity.Error, "End date is before the start date.")
            }
            if (e.current && e.end.isNotBlank()) {
                out += ProfileIssue("experience.${e.id}.end", IssueSeverity.Warning, "A current role usually has no end date.")
            }
        }
        profile.education.forEach { e ->
            if (e.institution.isBlank()) out += ProfileIssue("education.${e.id}.institution", IssueSeverity.Error, "Education needs an institution.")
        }
        val skills = profile.confirmedSkills().map { it.name.trim().lowercase() }
        skills.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            out += ProfileIssue("skills", IssueSeverity.Warning, "Duplicate skill: $it")
        }
        profile.confirmedProjects().forEach { p ->
            if (p.name.isBlank()) out += ProfileIssue("projects.${p.id}.name", IssueSeverity.Error, "A project needs a name.")
        }
        profile.preferences.minSalary?.let {
            if (it < 0) out += ProfileIssue("preferences.minSalary", IssueSeverity.Error, "Minimum salary cannot be negative.")
            if (profile.preferences.currency.isBlank()) {
                out += ProfileIssue("preferences.currency", IssueSeverity.Warning, "Add a currency for the salary preference.")
            }
        }
        val proposals = profile.proposedFactCount()
        if (proposals > 0) {
            out += ProfileIssue("profile.proposals", IssueSeverity.Warning,
                "$proposals imported or drafted fact(s) are waiting for confirmation and are not used in documents.")
        }
        if (profile.confirmedExperience().isEmpty()) {
            out += ProfileIssue("experience", IssueSeverity.Warning, "No confirmed employment history yet.")
        }
        if (profile.preferences.targetRoles.isEmpty()) {
            out += ProfileIssue("preferences.targetRoles", IssueSeverity.Info, "Add a target role to improve job matching.")
        }
        if (profile.workAuthorization.isBlank()) {
            out += ProfileIssue("workAuthorization", IssueSeverity.Info,
                "Work authorisation is empty. Add it only if you want it used in applications.")
        }
        return out
    }

    /** A short list of the most useful next actions, ordered by weight. */
    fun nextActions(profile: CandidateProfile, limit: Int = 5): List<String> =
        score(profile).sections.filterNot { it.complete }
            .sortedByDescending { it.weight - it.earned }
            .flatMap { s -> s.missing.map { "Add $it (${s.label})" } }
            .take(max(1, limit))
}

object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val URL = Regex("^https?://[^\\s/$.?#].[^\\s]*$", RegexOption.IGNORE_CASE)

    fun isEmail(value: String): Boolean = EMAIL.matches(value.trim())

    fun isWebUrl(value: String): Boolean = URL.matches(value.trim())

    /** Accept `linkedin.com/in/x` as a link; keep the candidate's text otherwise. */
    fun normalizeUrl(value: String): String {
        val s = value.trim()
        if (s.isEmpty()) return s
        return if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s else "https://$s"
    }

    fun bounded(value: String, max: Int): String = value.trim().let { if (it.length <= max) it else it.take(max) }
}

/**
 * A final safety net for text written by the model. It cannot prove prose is true, but it blocks
 * the concrete claims that matter most: contact details that are not the candidate's, invented
 * years of experience, credentials the profile does not contain, and unlisted skills.
 */
object TruthGuard {
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val YEARS = Regex("\\b(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)\\b", RegexOption.IGNORE_CASE)
    private val DEGREE = Regex("\\b(PhD|Ph\\.D\\.?|Doctorate|MBA|Master'?s?|Bachelor'?s?|B\\.Sc\\.?|M\\.Sc\\.?|CPA|CFA|PMP)\\b", RegexOption.IGNORE_CASE)
    private val SKILLS_LINE = Regex("(?im)^\\s*(?:core\\s+)?skills\\s*[:\\-]\\s*(.+)$")
    private val PHONE = Regex("\\+?\\d[\\d ()-]{6,}\\d")
    private val YEAR_RANGE = Regex("^(?:19|20)\\d{2}\\s*[-–—]\\s*(?:19|20)\\d{2}$")

    fun check(text: String, profile: CandidateProfile, now: YearMonth = YearMonth.now()): TruthCheck {
        val problems = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (text.length > 200_000) problems += "The document is too long."

        val candidateEmails = setOf(profile.email.trim().lowercase()).filter { it.isNotBlank() }
        EMAIL.findAll(text).map { it.value.lowercase() }.filter { it !in candidateEmails }.forEach {
            problems += "Uses an email address that is not on the profile: $it"
        }

        val digits = profile.phone.filter(Char::isDigit)
        if (digits.length >= 7) {
            PHONE.findAll(text).map { it.value.trim() }
                .filterNot { YEAR_RANGE.matches(it) }
                .filter { v -> v.filter(Char::isDigit) != digits }
                .forEach {
                    problems += "Uses a phone number that is not on the profile: $it"
                }
        }

        val realYears = CareerDates.experienceYears(profile, now)
        YEARS.findAll(text).map { it.groupValues[1].toInt() }.forEach { claimed ->
            if (claimed > 0) {
                val dated = profile.confirmedExperience().any { CareerDates.parse(it.start) != null }
                if (!dated) problems += "Claims $claimed years of experience, but the profile has no confirmed dated employment."
                else if (claimed > realYears + 1.0) {
                    problems += "Claims $claimed years of experience, but confirmed employment adds up to ${"%.1f".format(realYears)} years."
                }
            }
        }

        val credentials = buildString {
            profile.confirmedEducation().forEach { append(it.degree).append(' ').append(it.fieldOfStudy).append(' ') }
            profile.confirmedCertifications().forEach { append(it.name).append(' ') }
            profile.confirmedQualifications().forEach { append(it.name).append(' ') }
        }.lowercase()
        DEGREE.findAll(text).map { it.value }.distinct().forEach { original ->
            val claim = original.lowercase().replace(".", "")
            if (!degreeBackedBy(claim, credentials)) {
                problems += "Mentions a credential the profile does not contain: ${original.trim()}"
            }
        }

        val knownSkills = profile.confirmedSkills().map { it.name.trim().lowercase() }.toSet()
        SKILLS_LINE.findAll(text).forEach { m ->
            m.groupValues[1].split(',', ';', '|').map { it.trim() }.filter { it.isNotBlank() }.forEach { s ->
                if (s.lowercase() !in knownSkills) problems += "Skills line lists an unconfirmed skill: $s"
            }
        }

        if (profile.proposedFactCount() > 0) warnings += "Some profile facts are still unconfirmed and were not used."
        if (problems.isNotEmpty()) warnings += "Fix the flagged claims or confirm the facts in the profile first."
        return TruthCheck(problems.isEmpty(), problems.distinct(), warnings)
    }

    private fun degreeBackedBy(claim: String, credentials: String): Boolean {
        val c = credentials.lowercase()
        return when (claim.trim()) {
            "phd", "doctorate" -> listOf("phd", "ph.d", "doctor").any(c::contains)
            "mba", "master", "masters", "master's", "msc" ->
                listOf("master", "mba", "msc", "m.sc", "mba").any(c::contains)
            "bachelor", "bachelors", "bachelor's", "bsc" ->
                listOf("bachelor", "bsc", "b.sc", "b.a", "ba ").any(c::contains)
            "cpa" -> c.contains("cpa")
            "cfa" -> c.contains("cfa")
            "pmp" -> c.contains("pmp")
            else -> c.contains(claim)
        }
    }
}
