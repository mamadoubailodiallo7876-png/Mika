package com.wangzan101.mika.core.cap.career

import com.wangzan101.mika.core.cap.career.CareerToolSupport.choice
import com.wangzan101.mika.core.cap.career.CareerToolSupport.documentJson
import com.wangzan101.mika.core.cap.career.CareerToolSupport.guarded
import com.wangzan101.mika.core.cap.career.CareerToolSupport.jsonStrings
import com.wangzan101.mika.core.cap.career.CareerToolSupport.matchJson
import com.wangzan101.mika.core.cap.career.CareerToolSupport.profileJson
import com.wangzan101.mika.core.cap.career.CareerToolSupport.service
import com.wangzan101.mika.core.career.CandidateProfile
import com.wangzan101.mika.core.career.CareerDocumentKind
import com.wangzan101.mika.core.career.CareerException
import com.wangzan101.mika.core.career.CareerPreferences
import com.wangzan101.mika.core.career.CareerService
import com.wangzan101.mika.core.career.CertificationEntry
import com.wangzan101.mika.core.career.CoverLetterTone
import com.wangzan101.mika.core.career.DocumentFormat
import com.wangzan101.mika.core.career.EducationEntry
import com.wangzan101.mika.core.career.EmploymentType
import com.wangzan101.mika.core.career.ExperienceEntry
import com.wangzan101.mika.core.career.FactSource
import com.wangzan101.mika.core.career.JobMatcher
import com.wangzan101.mika.core.career.LanguageEntry
import com.wangzan101.mika.core.career.ProfileIssue
import com.wangzan101.mika.core.career.ProfileLinks
import com.wangzan101.mika.core.career.ProfileQuality
import com.wangzan101.mika.core.career.ProjectEntry
import com.wangzan101.mika.core.career.QualificationEntry
import com.wangzan101.mika.core.career.RemotePreference
import com.wangzan101.mika.core.career.SkillEntry
import com.wangzan101.mika.core.career.SkillVocabulary
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ArgError
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.tool.bool
import com.wangzan101.mika.core.tool.fail
import com.wangzan101.mika.core.tool.int
import com.wangzan101.mika.core.tool.ok
import com.wangzan101.mika.core.tool.str
import com.wangzan101.mika.core.tool.strArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Profile, documents and exports. Consolidated into a few action-based tools so the model sees a
 * small, clear tool surface; each action still validates and audits like a separate tool.
 */
internal object CareerProfileTool : AgentTool {
    private val ACTIONS = listOf(
        "read", "validate", "update", "set_preferences", "add_experience", "add_education", "add_skill",
        "add_project", "add_certification", "add_language", "add_qualification", "delete_entry",
        "confirm_imported", "extract",
    )

    override val verb = Verb.CareerWrite
    override val risk = Risk.Read
    override val capability = CAREER_CAPABILITY
    override val offScreen = true
    override val def = ToolDef(
        "career_profile",
        """
        Read or change the candidate's career profile. action=read returns confirmed facts, completeness and issues; validate checks the profile; update changes scalar fields; set_preferences changes matching preferences; add_* appends one confirmed entry; delete_entry removes an entry by id; confirm_imported accepts imported proposals; extract turns pasted CV text into unconfirmed proposals (optionally merged). Erasing all career data is a UI-only privacy action and is not exposed to the model. Never invent facts: only add what the user supplied or confirmed. Email and phone are personal data; do not repeat them unnecessarily.
        """.trimIndent(),
        mapOf(
            "action" to str("What to do.", ACTIONS),
            "entryId" to str("For delete_entry: the id shown by action=read."),
            "fullName" to str("Full name, at most 160 characters."),
            "headline" to str("Professional headline, at most 160 characters."),
            "summary" to str("The candidate's own professional summary, at most 4000 characters."),
            "email" to str("Contact email."),
            "phone" to str("Contact phone."),
            "location" to str("City/region, at most 160 characters."),
            "github" to str("GitHub link."),
            "linkedin" to str("LinkedIn link."),
            "portfolio" to str("Portfolio link."),
            "website" to str("Personal website link."),
            "workAuthorization" to str("Work authorisation exactly as the candidate stated it."),
            "targetRoles" to strArray("Target job titles.", 20),
            "preferredLocations" to strArray("Preferred locations.", 20),
            "remote" to str("Remote preference.", listOf("Unspecified", "Remote", "Hybrid", "OnSite")),
            "employmentTypes" to strArray("Preferred employment types: FullTime, PartTime, Contract, Internship, Temporary, Volunteer.", 6),
            "minSalary" to int("Minimum annual salary, if the candidate set one.", 0, 10_000_000),
            "currency" to str("Currency for the salary preference, e.g. USD."),
            "seniority" to str("Seniority preference, at most 80 characters."),
            "industries" to strArray("Industries of interest.", 20),
            "mustHaveSkills" to strArray("Skills a job must name; missing them caps the match score.", 20),
            "dealBreakers" to strArray("Phrases in a listing that cap the match score.", 20),
            "company" to str("For add_experience: employer."),
            "title" to str("For add_experience: job title."),
            "start" to str("Start date as written, e.g. 2021-03."),
            "end" to str("End date as written; leave empty for a current role."),
            "current" to bool("For add_experience: true if this is the current role."),
            "bullets" to strArray("Achievements for a role or project.", 10),
            "institution" to str("For add_education: school or university."),
            "degree" to str("For add_education: degree."),
            "fieldOfStudy" to str("For add_education: field of study."),
            "grade" to str("For add_education: grade, if stated."),
            "name" to str("For add_skill/add_project/add_certification/add_language/add_qualification: the name."),
            "level" to str("For add_skill: level, if the candidate stated one."),
            "years" to int("For add_skill: years of use, if stated.", 0, 60),
            "issuer" to str("For add_certification/add_qualification: issuer."),
            "year" to str("For add_certification/add_qualification: year."),
            "credentialId" to str("For add_certification: credential id, if the candidate wants it stored."),
            "proficiency" to str("For add_language: proficiency, e.g. Native."),
            "url" to str("For add_project: project URL."),
            "description" to str("For add_project: description."),
            "details" to str("Extra details for an education or qualification entry."),
            "text" to str("For extract: CV text the user pasted or a document reader returned."),
            "fileName" to str("For extract: original file name, for context."),
            "merge" to bool("For extract: merge the proposals into the profile as unconfirmed facts (default true)."),
        ),
        listOf("action"),
    )

    override fun readOnly(args: JsonObject): Boolean =
        (args["action"] as? JsonPrimitive)?.content in setOf("read", "validate")

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = guarded {
        val service = service(ctx)
        when (a.choice("action", ACTIONS)) {
            "read" -> {
                val p = service.profile()
                ok {
                    put("profile", profileJson(p))
                    put("issues", issuesJson(ProfileQuality.validate(p)))
                }
            }
            "validate" -> {
                val p = service.profile()
                val issues = ProfileQuality.validate(p)
                ok {
                    put("completeness", ProfileQuality.score(p).percent)
                    put("issues", issuesJson(issues))
                    put("nextActions", jsonStrings(ProfileQuality.nextActions(p)))
                }
            }
            "update", "set_preferences" -> {
                val existing = service.profile()
                val updated = applyScalars(existing, a)
                val saved = service.saveProfile(updated)
                ok {
                    put("profile", profileJson(saved.profile))
                    put("changed", true)
                }
            }
            "add_experience" -> {
                val existing = service.profile()
                val role = ExperienceEntry(
                    id = service.store.newId("exp"),
                    company = a.str("company", max = 160) ?: throw ArgError("company", "required"),
                    title = a.str("title", max = 160) ?: throw ArgError("title", "required"),
                    location = a.str("location", max = 160).orEmpty(),
                    start = a.str("start", max = 40).orEmpty(),
                    end = a.str("end", max = 40).orEmpty(),
                    current = a.bool("current", false),
                    summary = a.str("summary", max = 4000).orEmpty(),
                    bullets = a.strList("bullets", 0, 10, 300).orEmpty(),
                    source = FactSource.UserProvided, confirmed = true,
                )
                val saved = service.saveProfile(existing.copy(experience = existing.experience + role))
                ok { put("profile", profileJson(saved.profile)); put("entryId", role.id) }
            }
            "add_education" -> {
                val existing = service.profile()
                val entry = EducationEntry(
                    id = service.store.newId("edu"),
                    institution = a.str("institution", max = 200) ?: throw ArgError("institution", "required"),
                    degree = a.str("degree", max = 120).orEmpty(),
                    fieldOfStudy = a.str("fieldOfStudy", max = 120).orEmpty(),
                    start = a.str("start", max = 40).orEmpty(),
                    end = a.str("end", max = 40).orEmpty(),
                    grade = a.str("grade", max = 40).orEmpty(),
                    details = a.str("details", max = 1000).orEmpty(),
                    source = FactSource.UserProvided, confirmed = true,
                )
                val saved = service.saveProfile(existing.copy(education = existing.education + entry))
                ok { put("profile", profileJson(saved.profile)); put("entryId", entry.id) }
            }
            "add_skill" -> {
                val existing = service.profile()
                val name = a.str("name", max = 60) ?: throw ArgError("name", "required")
                val canonical = SkillVocabulary.canonical(name)
                if (existing.skills.any { SkillVocabulary.canonical(it.name) == canonical }) {
                    return@guarded fail("duplicate_skill", "$name is already on the profile.")
                }
                val entry = SkillEntry(
                    id = service.store.newId("skill"), name = name,
                    level = a.str("level", max = 40).orEmpty(), years = a.int("years", 0, 60),
                    source = FactSource.UserProvided, confirmed = true,
                )
                val saved = service.saveProfile(existing.copy(skills = existing.skills + entry))
                ok { put("profile", profileJson(saved.profile)); put("entryId", entry.id) }
            }
            "add_project" -> {
                val existing = service.profile()
                val entry = ProjectEntry(
                    id = service.store.newId("proj"),
                    name = a.str("name", max = 120) ?: throw ArgError("name", "required"),
                    url = a.str("url", max = 500).orEmpty(),
                    description = a.str("description", max = 2000).orEmpty(),
                    bullets = a.strList("bullets", 0, 10, 300).orEmpty(),
                    source = FactSource.UserProvided, confirmed = true,
                )
                val saved = service.saveProfile(existing.copy(projects = existing.projects + entry))
                ok { put("profile", profileJson(saved.profile)); put("entryId", entry.id) }
            }
            "add_certification" -> {
                val existing = service.profile()
                val entry = CertificationEntry(
                    id = service.store.newId("cert"),
                    name = a.str("name", max = 160) ?: throw ArgError("name", "required"),
                    issuer = a.str("issuer", max = 120).orEmpty(),
                    year = a.str("year", max = 20).orEmpty(),
                    credentialId = a.str("credentialId", max = 120).orEmpty(),
                    source = FactSource.UserProvided, confirmed = true,
                )
                val saved = service.saveProfile(existing.copy(certifications = existing.certifications + entry))
                ok { put("profile", profileJson(saved.profile)); put("entryId", entry.id) }
            }
            "add_language" -> {
                val existing = service.profile()
                val entry = LanguageEntry(
                    id = service.store.newId("lang"),
                    name = a.str("name", max = 60) ?: throw ArgError("name", "required"),
                    proficiency = a.str("proficiency", max = 60).orEmpty(),
                    source = FactSource.UserProvided, confirmed = true,
                )
                val saved = service.saveProfile(existing.copy(languages = existing.languages + entry))
                ok { put("profile", profileJson(saved.profile)); put("entryId", entry.id) }
            }
            "add_qualification" -> {
                val existing = service.profile()
                val entry = QualificationEntry(
                    id = service.store.newId("qual"),
                    name = a.str("name", max = 160) ?: throw ArgError("name", "required"),
                    issuer = a.str("issuer", max = 120).orEmpty(),
                    year = a.str("year", max = 20).orEmpty(),
                    details = a.str("details", max = 1000).orEmpty(),
                    source = FactSource.UserProvided, confirmed = true,
                )
                val saved = service.saveProfile(existing.copy(qualifications = existing.qualifications + entry))
                ok { put("profile", profileJson(saved.profile)); put("entryId", entry.id) }
            }
            "delete_entry" -> {
                val id = a.str("entryId", max = 120) ?: throw ArgError("entryId", "required")
                val existing = service.profile()
                val updated = existing.copy(
                    experience = existing.experience.filterNot { it.id == id },
                    education = existing.education.filterNot { it.id == id },
                    skills = existing.skills.filterNot { it.id == id },
                    projects = existing.projects.filterNot { it.id == id },
                    certifications = existing.certifications.filterNot { it.id == id },
                    languages = existing.languages.filterNot { it.id == id },
                    qualifications = existing.qualifications.filterNot { it.id == id },
                )
                if (updated == existing) return@guarded fail("not_found", "No profile entry has id $id.")
                val saved = service.saveProfile(updated)
                ok { put("profile", profileJson(saved.profile)); put("deleted", id) }
            }
            "confirm_imported" -> {
                val saved = service.confirmImportedFacts()
                ok { put("profile", profileJson(saved.profile)); put("confirmed", true) }
            }
            "extract" -> {
                val text = a.str("text", min = 40, max = 200_000) ?: throw ArgError("text", "required")
                val fileName = a.str("fileName", max = 200) ?: "pasted-cv.txt"
                val result = service.importProfileText(text, fileName)
                val merged = if (a.bool("merge", true)) service.mergeImportedProfile(result.suggestions) else null
                ok {
                    put("fields", JsonArray(result.fields.map { f -> buildJsonObject {
                        put("field", f.field); put("value", f.value); put("evidence", f.evidence); put("confidence", f.confidence)
                    } }))
                    put("warnings", jsonStrings(result.warnings))
                    put("sectionsFound", jsonStrings(result.sectionsFound))
                    put("proposedFacts", result.proposedCount)
                    put("merged", merged != null)
                    put("profile", profileJson((merged ?: service.data()).profile))
                    put("next", "Review the proposals and call career_profile action=confirm_imported before using them in documents.")
                }
            }
            else -> fail("unknown_action", "Unknown action.")
        }
    }

    private fun applyScalars(existing: CandidateProfile, a: Args): CandidateProfile {
        val links = ProfileLinks(
            github = a.str("github", max = 500)?.trim() ?: existing.links.github,
            linkedin = a.str("linkedin", max = 500)?.trim() ?: existing.links.linkedin,
            portfolio = a.str("portfolio", max = 500)?.trim() ?: existing.links.portfolio,
            website = a.str("website", max = 500)?.trim() ?: existing.links.website,
        )
        val prefs = existing.preferences
        val employmentTypes = a.strList("employmentTypes", 0, 6, 40)?.map { value ->
            EmploymentType.entries.firstOrNull { it.name.equals(value, true) }
                ?: throw ArgError("employmentTypes", "unknown type $value")
        } ?: prefs.employmentTypes
        val remote = a.choice("remote", listOf("Unspecified", "Remote", "Hybrid", "OnSite"))
            ?.let { RemotePreference.valueOf(it) } ?: prefs.remote
        val updatedPrefs = CareerPreferences(
            targetRoles = a.strList("targetRoles", 0, 20, 80) ?: prefs.targetRoles,
            preferredLocations = a.strList("preferredLocations", 0, 20, 80) ?: prefs.preferredLocations,
            remote = remote,
            employmentTypes = employmentTypes,
            minSalary = a.int("minSalary", 0, 10_000_000) ?: prefs.minSalary,
            currency = a.str("currency", max = 8)?.trim() ?: prefs.currency,
            seniority = a.str("seniority", max = 80)?.trim() ?: prefs.seniority,
            industries = a.strList("industries", 0, 20, 80) ?: prefs.industries,
            mustHaveSkills = a.strList("mustHaveSkills", 0, 20, 60) ?: prefs.mustHaveSkills,
            dealBreakers = a.strList("dealBreakers", 0, 20, 60) ?: prefs.dealBreakers,
            weights = prefs.weights,
        )
        return existing.copy(
            fullName = a.str("fullName", max = 160)?.trim() ?: existing.fullName,
            headline = a.str("headline", max = 160)?.trim() ?: existing.headline,
            summary = a.str("summary", max = 4000)?.trim() ?: existing.summary,
            email = a.str("email", max = 200)?.trim() ?: existing.email,
            phone = a.str("phone", max = 60)?.trim() ?: existing.phone,
            location = a.str("location", max = 160)?.trim() ?: existing.location,
            links = links,
            workAuthorization = a.str("workAuthorization", max = 300)?.trim() ?: existing.workAuthorization,
            preferences = updatedPrefs,
        )
    }

    private fun issuesJson(issues: List<ProfileIssue>): JsonArray = JsonArray(issues.map { issue ->
        buildJsonObject {
            put("field", issue.field)
            put("severity", issue.severity.name)
            put("message", issue.message)
        }
    })
}
