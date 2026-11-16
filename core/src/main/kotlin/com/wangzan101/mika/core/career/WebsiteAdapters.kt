package com.wangzan101.mika.core.career

import java.net.URI

data class FormField(
    val label: String,
    val name: String = "",
    val type: String = "text",
    val required: Boolean = false,
    val options: List<String> = emptyList(),
)

data class FieldMapping(
    val canonical: String,
    val label: String,
    val value: String,
    val source: String,
    val confidence: Double,
    val requiresUser: Boolean = false,
)

data class FormAnalysis(
    val adapter: WebsiteAdapter?,
    val mappings: List<FieldMapping>,
    val unresolvedRequired: List<String>,
    val sensitiveFields: List<String>,
    val questionsForUser: List<String>,
    val fileUploads: List<String>,
    val steps: List<String>,
    val warnings: List<String>,
) {
    val canAutoFill: Boolean get() = mappings.any { !it.requiresUser && it.value.isNotBlank() }
}

/**
 * Website adapters are data, not code branches. Each one describes how a site is recognised and
 * which parts of an application it supports. Nothing here bypasses sign-in, CAPTCHAs, MFA, identity
 * checks or site terms; those steps always go to the user.
 */
object WebsiteAdapters {
    val builtIn: List<WebsiteAdapter> = listOf(
        WebsiteAdapter(
            "linkedin", "LinkedIn", listOf("linkedin.com"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.AccountRegistration, AdapterCapability.ApplicationForm,
                AdapterCapability.ResumeUpload, AdapterCapability.StatusTracking),
            notes = "Use the public job page. Sign-in, Easy Apply and any verification step are handed to the user.",
            termsNote = "LinkedIn's terms restrict automated access. Prefer its public job pages and the user's own signed-in session.",
        ),
        WebsiteAdapter(
            "indeed", "Indeed", listOf("indeed.com", "indeed.co.uk", "indeed.ca", "indeed.com.au"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.AccountRegistration, AdapterCapability.ApplicationForm,
                AdapterCapability.ResumeUpload, AdapterCapability.CoverLetter),
            notes = "Job pages can be read; account creation, verification and submission need the user.",
            termsNote = "Indeed's terms restrict scraping. Use pages the user is already viewing and never bypass access controls.",
        ),
        WebsiteAdapter(
            "glassdoor", "Glassdoor", listOf("glassdoor.com", "glassdoor.co.uk"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.AccountRegistration, AdapterCapability.ApplicationForm),
            notes = "Often redirects to the employer's own ATS; identify the final site before preparing forms.",
            termsNote = "Respect Glassdoor's access rules; do not attempt to defeat sign-in walls.",
        ),
        WebsiteAdapter(
            "greenhouse", "Greenhouse", listOf("greenhouse.io", "boards.greenhouse.io", "job-boards.greenhouse.io"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.OfficialApi, AdapterCapability.ApplicationForm,
                AdapterCapability.ResumeUpload, AdapterCapability.CoverLetter, AdapterCapability.StatusTracking),
            notes = "Public job-board pages are stable and ATS-style; the board API is documented and permitted.",
            termsNote = "Use the public job board; do not submit applications without the user's approval.",
        ),
        WebsiteAdapter(
            "lever", "Lever", listOf("lever.co", "jobs.lever.co"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.OfficialApi, AdapterCapability.ApplicationForm,
                AdapterCapability.ResumeUpload, AdapterCapability.CoverLetter),
            notes = "Public postings are available from the job board; forms usually need name, email, resume and sometimes custom questions.",
            termsNote = "Use public postings and the user's own session for submission.",
        ),
        WebsiteAdapter(
            "workday", "Workday", listOf("myworkdayjobs.com", "workday.com", "wd1.myworkdayjobs.com", "wd3.myworkdayjobs.com", "wd5.myworkdayjobs.com"),
            listOf(AdapterCapability.AccountRegistration, AdapterCapability.ApplicationForm, AdapterCapability.ResumeUpload,
                AdapterCapability.CoverLetter, AdapterCapability.StatusTracking),
            notes = "Workday requires an account; creating it is a user-approved step. Fields often appear across several pages.",
            termsNote = "Never create an account without explicit user approval, and never bypass MFA.",
        ),
        WebsiteAdapter(
            "smartrecruiters", "SmartRecruiters", listOf("smartrecruiters.com", "jobs.smartrecruiters.com"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.OfficialApi, AdapterCapability.ApplicationForm, AdapterCapability.ResumeUpload),
            notes = "Public company job boards expose stable posting pages.",
            termsNote = "Use public posting pages; submission is a user-approved action.",
        ),
        WebsiteAdapter(
            "usajobs", "USAJOBS", listOf("usajobs.gov"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.OfficialApi, AdapterCapability.ApplicationForm,
                AdapterCapability.ResumeUpload, AdapterCapability.StatusTracking),
            notes = "Government postings state strict eligibility and citizenship rules; surface them and let the user decide.",
            termsNote = "USAJOBS publishes an official API. Eligibility and identity checks remain the user's responsibility.",
        ),
        WebsiteAdapter(
            "remoteok", "Remote OK", listOf("remoteok.com"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.OfficialApi, AdapterCapability.ApplicationForm),
            notes = "Remote listings; verify the employer and location requirements on the original posting.",
            termsNote = "Respect the API and site terms.",
        ),
        WebsiteAdapter(
            "weworkremotely", "We Work Remotely", listOf("weworkremotely.com"),
            listOf(AdapterCapability.JobSearch, AdapterCapability.ApplicationForm),
            notes = "Remote listings; applications usually go to the employer's own page.",
            termsNote = "Read public listings; do not bypass sign-in or rate limits.",
        ),
        WebsiteAdapter(
            "generic", "Company career site", emptyList(),
            listOf(AdapterCapability.JobSearch, AdapterCapability.ApplicationForm, AdapterCapability.ResumeUpload, AdapterCapability.CoverLetter),
            notes = "Unknown ATS. Inspect the form labels, fill only verified information, and hand every unclear or sensitive field to the user.",
            termsNote = "Automate only what the site permits. Never defeat CAPTCHAs, MFA or access restrictions.",
        ),
    )

    fun forUrl(url: String): WebsiteAdapter? {
        val host = runCatching { URI(if (url.contains("://")) url else "https://$url").host?.lowercase() }.getOrNull() ?: return null
        return builtIn.filter { it.hosts.isNotEmpty() }
            .maxByOrNull { adapter -> adapter.hosts.count { host == it || host.endsWith(".$it") } }
            ?.takeIf { adapter -> adapter.hosts.any { host == it || host.endsWith(".$it") } }
    }

    fun generic(): WebsiteAdapter = builtIn.last()

    fun withCustom(custom: List<WebsiteAdapter>): List<WebsiteAdapter> = builtIn + custom

    val canonicalFields: List<String> = listOf(
        "firstName", "lastName", "fullName", "email", "phone", "location", "linkedin", "github", "portfolio",
        "resume", "coverLetter", "workAuthorization", "salaryExpectation", "startDate", "customQuestion",
    )
}

/**
 * Maps form labels to candidate facts. It never guesses a sensitive value, and it reports every
 * required field it cannot fill instead of inventing one.
 */
object FormFieldMapper {
    private val SENSITIVE = Regex(
        "(?i)\\b(password|passcode|ssn|social security|national id|passport|visa number|bank|iban|credit card|" +
            "date of birth|dob|mother'?s maiden|security answer|tax id|government id)\\b",
    )
    private val FILE = Regex("(?i)\\b(resume|cv|curriculum vitae|cover letter|attachment|upload)\\b")
    private val QUESTION_TYPE = setOf("textarea", "select", "radio", "checkbox")

    private data class Synonym(val canonical: String, val patterns: List<Regex>)

    private fun rx(vararg words: String): List<Regex> = words.map { Regex("(?i)\\b${Regex.escape(it)}\\b") }

    private val SYNONYMS = listOf(
        Synonym("firstName", rx("first name", "given name", "forename", "firstname")),
        Synonym("lastName", rx("last name", "surname", "family name", "lastname")),
        Synonym("fullName", rx("full name", "your name", "applicant name", "legal name")),
        Synonym("email", rx("email", "e-mail", "email address", "contact email")),
        Synonym("phone", rx("phone", "mobile", "telephone", "contact number", "phone number", "cell")),
        Synonym("location", rx("location", "city", "town", "current location", "where are you based")),
        Synonym("linkedin", rx("linkedin", "linkedin profile", "linkedin url")),
        Synonym("github", rx("github", "github profile", "github url")),
        Synonym("portfolio", rx("portfolio", "website", "personal website", "portfolio url")),
        Synonym("resume", rx("resume", "cv", "curriculum vitae", "attach resume", "upload resume", "upload cv")),
        Synonym("coverLetter", rx("cover letter", "covering letter", "motivation letter")),
        Synonym("workAuthorization", rx("work authorization", "work authorisation", "right to work", "visa status", "legally authorized")),
        Synonym("salaryExpectation", rx("salary expectation", "expected salary", "desired salary", "compensation expectation")),
        Synonym("startDate", rx("start date", "available start date", "notice period", "when can you start")),
    )

    fun analyze(
        fields: List<FormField>,
        profile: CandidateProfile,
        documents: List<CareerDocument>,
        job: JobPosting? = null,
        adapter: WebsiteAdapter? = null,
    ): FormAnalysis {
        val mappings = mutableListOf<FieldMapping>()
        val unresolved = mutableListOf<String>()
        val sensitive = mutableListOf<String>()
        val questions = mutableListOf<String>()
        val uploads = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val resume = documents.filter { it.kind == CareerDocumentKind.Resume }.maxByOrNull { it.updatedAtMs }
        val cover = documents.filter { it.kind == CareerDocumentKind.CoverLetter }.maxByOrNull { it.updatedAtMs }

        val nameParts = profile.fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        for (field in fields) {
            val haystack = "${field.label} ${field.name}".trim()
            val sensitiveHit = SENSITIVE.containsMatchIn(haystack)
            if (sensitiveHit) {
                sensitive += field.label.ifBlank { field.name }
                mappings += FieldMapping("customQuestion", field.label, "", "sensitive field", 0.0, requiresUser = true)
                if (field.required) unresolved += field.label
                continue
            }
            if (Regex("(?i)\\b(company|employer|organisation|organization|job title|position|reference|recruiter)\\b").containsMatchIn(haystack)) {
                if (field.required) unresolved += field.label
                mappings += FieldMapping("customQuestion", field.label, "", "not a candidate profile field", 0.0, requiresUser = true)
                continue
            }
            val isFile = field.type.equals("file", true) || (FILE.containsMatchIn(haystack) && field.type.equals("text", true))
            val canonical = SYNONYMS.firstOrNull { s -> s.patterns.any { it.containsMatchIn(haystack) } }?.canonical
                ?: if (SkillVocabulary.normalize(haystack) in setOf("name", "your name")) "fullName" else null
            if (canonical == null || (isFile && canonical !in setOf("resume", "coverLetter"))) {
                if (QUESTION_TYPE.contains(field.type.lowercase())) {
                    questions += field.label.ifBlank { field.name }
                    mappings += FieldMapping("customQuestion", field.label, "", "needs the candidate's answer", 0.0, requiresUser = true)
                    if (field.required) unresolved += field.label
                } else if (field.required) {
                    unresolved += field.label
                    warnings += "No verified value for required field: ${field.label}"
                }
                continue
            }
            val (value, source, available) = valueFor(canonical, profile, nameParts, resume, cover)
            if (isFile) uploads += field.label.ifBlank { canonical }
            if (available && value.isNotBlank()) {
                mappings += FieldMapping(canonical, field.label, value, source, confidenceFor(canonical))
            } else {
                mappings += FieldMapping(canonical, field.label, "", source, 0.0, requiresUser = true)
                if (field.required) unresolved += field.label
            }
        }

        val requiresAccount = adapter?.capabilities?.contains(AdapterCapability.AccountRegistration) == true
        val steps = buildList {
            add("Confirm the employer and the original listing before filling anything")
            if (requiresAccount) add("Ask the user to create or sign in to the account (the assistant never creates an account alone)")
            add("Fill the fields mapped from the confirmed profile and review every value")
            if (uploads.isNotEmpty()) add("Attach the selected resume/cover letter files")
            if (questions.isNotEmpty()) add("Ask the user for answers to the remaining questions")
            add("Review the completed form with the user and request final approval")
            add("Submit only after approval, then capture the confirmation reference or message")
        }
        if (adapter == null) warnings += "No adapter matched this site; use the generic career-site plan and inspect each field."
        if (adapter != null && AdapterCapability.ApplicationForm !in adapter.capabilities) {
            warnings += "${adapter.name} is not marked as supporting structured application forms; hand the form to the user."
        }
        warnings += "This plan does not bypass CAPTCHAs, sign-in, MFA, identity checks or site restrictions."
        if (job?.suspicious?.isNotEmpty() == true) warnings += "Safety flags on this listing: ${job.suspicious.joinToString("; ")}"
        return FormAnalysis(
            adapter = adapter,
            mappings = mappings,
            unresolvedRequired = unresolved.distinct(),
            sensitiveFields = sensitive.distinct(),
            questionsForUser = questions.distinct(),
            fileUploads = uploads.distinct(),
            steps = steps,
            warnings = warnings.distinct(),
        )
    }

    private fun valueFor(
        canonical: String,
        profile: CandidateProfile,
        nameParts: List<String>,
        resume: CareerDocument?,
        cover: CareerDocument?,
    ): Triple<String, String, Boolean> = when (canonical) {
        "firstName" -> Triple(nameParts.firstOrNull().orEmpty(), "profile full name", nameParts.isNotEmpty())
        "lastName" -> Triple(nameParts.drop(1).joinToString(" "), "profile full name", nameParts.size > 1)
        "fullName" -> Triple(profile.fullName, "profile", profile.fullName.isNotBlank())
        "email" -> Triple(profile.email, "profile", profile.email.isNotBlank())
        "phone" -> Triple(profile.phone, "profile", profile.phone.isNotBlank())
        "location" -> Triple(profile.location, "profile", profile.location.isNotBlank())
        "linkedin" -> Triple(profile.links.linkedin, "profile link", profile.links.linkedin.isNotBlank())
        "github" -> Triple(profile.links.github, "profile link", profile.links.github.isNotBlank())
        "portfolio" -> Triple(
            profile.links.portfolio.ifBlank { profile.links.website }, "profile link",
            profile.links.portfolio.isNotBlank() || profile.links.website.isNotBlank(),
        )
        "resume" -> Triple(resume?.title.orEmpty(), resume?.let { "document ${it.id}" } ?: "no resume", resume != null)
        "coverLetter" -> Triple(cover?.title.orEmpty(), cover?.let { "document ${it.id}" } ?: "no cover letter", cover != null)
        "workAuthorization" -> Triple(profile.workAuthorization, "profile", profile.workAuthorization.isNotBlank())
        "salaryExpectation" -> Triple(
            profile.preferences.minSalary?.let { "$it ${profile.preferences.currency}".trim() }.orEmpty(),
            "preference", profile.preferences.minSalary != null,
        )
        "startDate" -> Triple("", "needs the candidate's answer", false)
        else -> Triple("", "needs the candidate's answer", false)
    }

    private fun confidenceFor(canonical: String): Double = when (canonical) {
        "email", "fullName", "phone" -> 0.95
        "linkedin", "github", "portfolio" -> 0.9
        "resume", "coverLetter" -> 0.9
        else -> 0.75
    }
}
