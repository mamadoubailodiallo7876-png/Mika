package com.wangzan101.mika.core.career

import java.time.YearMonth
import kotlin.math.floor

data class CvOptions(
    val template: String = "ats-classic",
    val includeSummary: Boolean = true,
    val includeProjects: Boolean = true,
    val includeEligibility: Boolean = false,
    val maxRoles: Int = 8,
    val maxBulletsPerRole: Int = 5,
)

enum class CoverLetterTone { Professional, Concise, Enthusiastic }

/**
 * Builds documents from confirmed profile facts only. Both the full CV and the tailored CV keep
 * an audit trail of the fact ids they used, and every claim is copied or derived from those facts.
 */
object DocumentEngine {
    fun generateCv(profile: CandidateProfile, options: CvOptions = CvOptions(), now: YearMonth = YearMonth.now()): GeneratedDocument {
        val facts = profile.allFactIds()
        val name = profile.fullName.ifBlank { "Candidate" }
        val body = buildString {
            appendLine(name.uppercase())
            if (profile.headline.isNotBlank()) appendLine(profile.headline.trim())
            contactLine(profile)?.let { appendLine(it) }
            if (options.includeEligibility && profile.workAuthorization.isNotBlank()) {
                appendLine("Work authorisation: ${profile.workAuthorization.trim()}")
            }
            appendLine()
            if (options.includeSummary) {
                val summary = profile.summary.ifBlank {
                    derivedSummary(profile, null, now)
                }
                if (summary.isNotBlank()) {
                    appendLine("SUMMARY")
                    appendLine(summary.trim())
                    appendLine()
                }
            }
            val skills = profile.confirmedSkills().map { it.name.trim() }.filter { it.isNotBlank() }
            if (skills.isNotEmpty()) {
                appendLine("SKILLS")
                appendLine(skills.joinToString(", "))
                appendLine()
            }
            val experience = profile.confirmedExperience().sortedByDescending { sortKey(it.start) }.take(options.maxRoles)
            if (experience.isNotEmpty()) {
                appendLine("EXPERIENCE")
                experience.forEach { role ->
                    appendLine("${role.title.trim().ifBlank { "Role" }} — ${role.company.trim()}")
                    appendLine(roleMeta(role))
                    if (role.summary.isNotBlank()) appendLine(role.summary.trim())
                    role.bullets.map(String::trim).filter(String::isNotBlank).take(options.maxBulletsPerRole).forEach {
                        appendLine("- $it")
                    }
                    appendLine()
                }
            }
            if (options.includeProjects) {
                val projects = profile.confirmedProjects()
                if (projects.isNotEmpty()) {
                    appendLine("PROJECTS")
                    projects.forEach { project ->
                        appendLine(project.name.trim() + project.url.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty())
                        if (project.description.isNotBlank()) appendLine(project.description.trim())
                        project.bullets.map(String::trim).filter(String::isNotBlank).take(options.maxBulletsPerRole).forEach {
                            appendLine("- $it")
                        }
                    }
                    appendLine()
                }
            }
            val education = profile.confirmedEducation()
            if (education.isNotEmpty()) {
                appendLine("EDUCATION")
                education.forEach { e ->
                    val degree = listOf(e.degree.trim(), e.fieldOfStudy.trim()).filter { it.isNotBlank() }.joinToString(", ")
                    appendLine(listOf(e.institution.trim(), degree).filter { it.isNotBlank() }.joinToString(" — "))
                    val meta = listOf(dateRange(e.start, e.end), e.grade.trim()).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) appendLine(meta)
                    if (e.details.isNotBlank()) appendLine(e.details.trim())
                    appendLine()
                }
            }
            val certifications = profile.confirmedCertifications()
            val qualifications = profile.confirmedQualifications()
            if (certifications.isNotEmpty() || qualifications.isNotEmpty()) {
                appendLine("CERTIFICATIONS & QUALIFICATIONS")
                certifications.forEach { c ->
                    appendLine(listOf(c.name.trim(), c.issuer.trim(), c.year.trim()).filter { it.isNotBlank() }.joinToString(" — "))
                }
                qualifications.forEach { q ->
                    appendLine(listOf(q.name.trim(), q.issuer.trim(), q.year.trim()).filter { it.isNotBlank() }.joinToString(" — "))
                }
                appendLine()
            }
            val languages = profile.confirmedLanguages()
            if (languages.isNotEmpty()) {
                appendLine("LANGUAGES")
                appendLine(languages.joinToString(", ") { language ->
                    language.name.trim() + language.proficiency.trim().takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                })
                appendLine()
            }
        }.trimEnd()
        return GeneratedDocument(CareerDocumentKind.Resume, "$name — Resume", body, facts)
    }

    /**
     * Tailors ordering and emphasis to a listing. It never adds an experience, skill or metric:
     * only confirmed facts are re-ordered and the summary names the target role/company.
     */
    fun tailorCv(
        profile: CandidateProfile,
        job: JobPosting,
        options: CvOptions = CvOptions(template = "ats-tailored"),
        now: YearMonth = YearMonth.now(),
    ): GeneratedDocument {
        val relevant = JobMatcher.requiredSkills(profile, job)
        val mine = profile.confirmedSkills()
        val ordered = mine.sortedWith(
            compareByDescending<SkillEntry> { entry -> relevant.any { it == SkillVocabulary.canonical(entry.name) } }
                .thenBy { it.name },
        )
        val tailored = profile.copy(skills = ordered, summary = derivedSummary(profile, job, now))
        val doc = generateCv(tailored, options, now)
        return doc.copy(
            title = "${profile.fullName.ifBlank { "Candidate" }} — Resume for ${job.company.ifBlank { job.title }}",
        )
    }

    fun coverLetterDraft(
        profile: CandidateProfile,
        job: JobPosting,
        tone: CoverLetterTone = CoverLetterTone.Professional,
        now: YearMonth = YearMonth.now(),
    ): GeneratedDocument {
        val name = profile.fullName.ifBlank { "Candidate" }
        val relevant = JobMatcher.requiredSkills(profile, job)
        val matched = profile.confirmedSkills().map { it.name to SkillVocabulary.canonical(it.name) }
            .filter { (_, c) -> relevant.any { it == c } }.map { it.first }.distinct()
        val roles = profile.confirmedExperience().sortedByDescending { sortKey(it.start) }.take(2)
        val opening = when (tone) {
            CoverLetterTone.Professional -> "Dear Hiring Team,"
            CoverLetterTone.Concise -> "Dear Hiring Team,"
            CoverLetterTone.Enthusiastic -> "Dear Hiring Team,"
        }
        val intro = when (tone) {
            CoverLetterTone.Professional ->
                "I am writing to apply for the ${job.title.trim()} role${job.company.takeIf { it.isNotBlank() }?.let { " at ${it.trim()}" }.orEmpty()}."
            CoverLetterTone.Concise ->
                "I am applying for the ${job.title.trim()} role${job.company.takeIf { it.isNotBlank() }?.let { " at ${it.trim()}" }.orEmpty()}."
            CoverLetterTone.Enthusiastic ->
                "I am excited to apply for the ${job.title.trim()} role${job.company.takeIf { it.isNotBlank() }?.let { " at ${it.trim()}" }.orEmpty()}."
        }
        val body = buildString {
            appendLine(opening)
            appendLine()
            appendLine(intro)
            appendLine()
            val who = listOf(profile.headline.trim(), derivedSummary(profile, job, now)).firstOrNull { it.isNotBlank() }.orEmpty()
            if (who.isNotBlank()) {
                appendLine(who.trim().let { if (it.endsWith('.')) it else "$it." })
                appendLine()
            }
            if (roles.isNotEmpty()) {
                val role = roles.first()
                appendLine(
                    "In my role as ${role.title.trim()} at ${role.company.trim()}, " +
                        (role.bullets.firstOrNull { it.isNotBlank() }?.trim()?.let { "I ${it.removePrefix("-").trim().replaceFirstChar(Char::lowercase)}." }
                            ?: "I worked on ${role.summary.trim().ifBlank { "the responsibilities described in my resume" }}."),
                )
                appendLine()
            }
            if (matched.isNotEmpty()) {
                appendLine("The role's focus on ${relevant.take(4).joinToString()} connects directly with my confirmed experience in ${matched.take(4).joinToString()}.")
                appendLine()
            }
            appendLine("I would welcome the opportunity to discuss how my confirmed experience fits the ${job.title.trim()} role. Thank you for your time and consideration.")
            appendLine()
            appendLine("Sincerely,")
            appendLine(name)
            contactLine(profile)?.let { appendLine(it) }
        }.trimEnd()
        return GeneratedDocument(
            CareerDocumentKind.CoverLetter,
            "Cover letter — ${job.company.ifBlank { job.title }}",
            body,
            profile.allFactIds(),
        )
    }

    /** Checks a model-written letter or CV before it is stored. */
    fun reviewDraft(text: String, profile: CandidateProfile, now: YearMonth = YearMonth.now()): TruthCheck =
        TruthGuard.check(text, profile, now)

    private fun derivedSummary(profile: CandidateProfile, job: JobPosting?, now: YearMonth): String {
        val years = floor(CareerDates.experienceYears(profile, now)).toInt()
        val skills = profile.confirmedSkills().map { it.name.trim() }.filter { it.isNotBlank() }
        val relevant = job?.let { j ->
            val req = JobMatcher.requiredSkills(profile, j).toSet()
            skills.filter { SkillVocabulary.canonical(it) in req }
        }.orEmpty()
        val highlight = (relevant.ifEmpty { skills }).take(4)
        return buildString {
            when {
                job != null && job.title.isNotBlank() -> append("${profile.headline.trim().ifBlank { "Professional" }} targeting the ${job.title.trim()} role")
                profile.headline.isNotBlank() -> append(profile.headline.trim())
                else -> append("Professional")
            }
            if (years >= 1) append(" with $years year${if (years == 1) "" else "s"} of confirmed experience")
            if (highlight.isNotEmpty()) {
                append(if (years >= 1) ", working with " else " working with ")
                append(highlight.joinToString())
            }
            append(".")
        }
    }

    private fun contactLine(profile: CandidateProfile): String? = listOf(
        profile.email.trim(), profile.phone.trim(), profile.location.trim(),
        profile.links.linkedin.trim(), profile.links.github.trim(), profile.links.portfolio.trim(), profile.links.website.trim(),
    ).filter { it.isNotBlank() }.joinToString(" | ").ifBlank { null }

    private fun roleMeta(role: ExperienceEntry): String = listOf(
        role.location.trim(), dateRange(role.start, role.end, role.current),
    ).filter { it.isNotBlank() }.joinToString(" · ")

    private fun dateRange(start: String, end: String, current: Boolean = false): String = when {
        start.isBlank() && end.isBlank() -> ""
        start.isBlank() -> end
        current -> "$start — Present"
        end.isBlank() -> start
        else -> "$start — $end"
    }

    private fun sortKey(start: String): Int = start.filter(Char::isDigit).take(6).toIntOrNull() ?: 0
}
