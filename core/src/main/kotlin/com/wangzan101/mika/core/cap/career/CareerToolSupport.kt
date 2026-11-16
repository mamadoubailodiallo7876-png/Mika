package com.wangzan101.mika.core.cap.career

import com.wangzan101.mika.core.career.ApplicationRecord
import com.wangzan101.mika.core.career.CandidateProfile
import com.wangzan101.mika.core.career.CareerDocument
import com.wangzan101.mika.core.career.CareerException
import com.wangzan101.mika.core.career.CareerService
import com.wangzan101.mika.core.career.JobMatch
import com.wangzan101.mika.core.career.JobPosting
import com.wangzan101.mika.core.career.ProfileQuality
import com.wangzan101.mika.core.career.SkillVocabulary
import com.wangzan101.mika.core.career.proposedFactCount
import com.wangzan101.mika.core.capability.CapabilityKey
import com.wangzan101.mika.core.tool.ArgError
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.fail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CareerToolSupport {
    val SERVICE = CapabilityKey<CareerService>("career.service")

    fun service(ctx: ToolContext): CareerService =
        ctx[SERVICE] ?: throw IllegalStateException("career capability is not attached to this task")

    inline fun guarded(block: () -> ToolExecution): ToolExecution = try {
        block()
    } catch (e: CareerException) {
        fail("career_error", e.message ?: "career operation failed")
    } catch (e: ArgError) {
        fail("invalid_arguments", e.message ?: "invalid arguments")
    }

    fun Args.choice(name: String, values: List<String>): String? {
        val value = str(name) ?: return null
        if (value !in values) throw ArgError(name, "must be one of ${values.joinToString()}")
        return value
    }

    fun jsonStrings(values: Iterable<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

    fun JsonObjectBuilder.putIf(name: String, value: String?) {
        if (!value.isNullOrBlank()) put(name, value)
    }

    fun profileJson(profile: CandidateProfile): JsonObject = buildJsonObject {
        put("fullName", profile.fullName)
        put("headline", profile.headline)
        if (profile.summary.isNotBlank()) put("summary", profile.summary)
        put("email", profile.email)
        put("phone", profile.phone)
        put("location", profile.location)
        put("links", buildJsonObject {
            putIf("github", profile.links.github)
            putIf("linkedin", profile.links.linkedin)
            putIf("portfolio", profile.links.portfolio)
            putIf("website", profile.links.website)
        })
        if (profile.workAuthorization.isNotBlank()) put("workAuthorization", profile.workAuthorization)
        put("preferences", buildJsonObject {
            put("targetRoles", jsonStrings(profile.preferences.targetRoles))
            put("preferredLocations", jsonStrings(profile.preferences.preferredLocations))
            put("remote", profile.preferences.remote.name)
            put("employmentTypes", jsonStrings(profile.preferences.employmentTypes.map { it.name }))
            profile.preferences.minSalary?.let { put("minSalary", it) }
            putIf("currency", profile.preferences.currency)
            put("mustHaveSkills", jsonStrings(profile.preferences.mustHaveSkills))
            put("dealBreakers", jsonStrings(profile.preferences.dealBreakers))
        })
        put("experience", JsonArray(profile.experience.map { e -> buildJsonObject {
            put("id", e.id); put("company", e.company); put("title", e.title)
            putIf("location", e.location); putIf("start", e.start); putIf("end", e.end)
            put("current", e.current); put("confirmed", e.confirmed); put("source", e.source.name)
            if (e.summary.isNotBlank()) put("summary", e.summary)
            put("bullets", jsonStrings(e.bullets))
        } }))
        put("education", JsonArray(profile.education.map { e -> buildJsonObject {
            put("id", e.id); put("institution", e.institution); putIf("degree", e.degree)
            putIf("fieldOfStudy", e.fieldOfStudy); putIf("start", e.start); putIf("end", e.end)
            put("confirmed", e.confirmed); put("source", e.source.name)
        } }))
        put("skills", JsonArray(profile.skills.map { s -> buildJsonObject {
            put("id", s.id); put("name", s.name); putIf("level", s.level); s.years?.let { put("years", it) }
            put("confirmed", s.confirmed); put("source", s.source.name)
        } }))
        put("certifications", JsonArray(profile.certifications.map { c -> buildJsonObject {
            put("id", c.id); put("name", c.name); putIf("issuer", c.issuer); putIf("year", c.year)
            put("confirmed", c.confirmed)
        } }))
        put("projects", JsonArray(profile.projects.map { p -> buildJsonObject {
            put("id", p.id); put("name", p.name); putIf("url", p.url); putIf("description", p.description)
            put("bullets", jsonStrings(p.bullets)); put("confirmed", p.confirmed)
        } }))
        put("languages", JsonArray(profile.languages.map { l -> buildJsonObject {
            put("id", l.id); put("name", l.name); putIf("proficiency", l.proficiency); put("confirmed", l.confirmed)
        } }))
        put("qualifications", JsonArray(profile.qualifications.map { q -> buildJsonObject {
            put("id", q.id); put("name", q.name); putIf("issuer", q.issuer); putIf("year", q.year)
            put("confirmed", q.confirmed)
        } }))
        val score = ProfileQuality.score(profile)
        put("completeness", score.percent)
        put("missing", jsonStrings(score.missing))
        put("proposedFacts", profile.proposedFactCount())
    }

    fun jobJson(job: JobPosting): JsonObject = buildJsonObject {
        put("id", job.id); put("title", job.title); put("company", job.company)
        put("location", job.location); put("remote", job.remote.name)
        put("employmentType", job.employmentType.name); put("status", job.status.name)
        putIf("salaryText", job.salaryText); putIf("deadline", job.deadline)
        putIf("source", job.source); putIf("url", job.url)
        put("skills", jsonStrings(job.skills)); put("requirements", jsonStrings(job.requirements))
        job.experienceYears?.let { put("experienceYears", it) }
        if (job.educationRequirement.isNotBlank()) put("educationRequirement", job.educationRequirement)
        put("suspicious", jsonStrings(job.suspicious))
        put("discoveredAtMs", job.discoveredAtMs)
        if (job.description.isNotBlank()) put("description", job.description.take(6000))
    }

    fun matchJson(match: JobMatch): JsonObject = buildJsonObject {
        put("jobId", match.jobId); put("score", match.score)
        put("components", JsonArray(match.components.map { c -> buildJsonObject {
            put("label", c.label); put("score", c.score); put("weight", c.weight); put("detail", c.detail)
        } }))
        put("matchedSkills", jsonStrings(match.matchedSkills))
        put("missingSkills", jsonStrings(match.missingSkills))
        put("missingQualifications", jsonStrings(match.missingQualifications))
        put("strengths", jsonStrings(match.strengths))
        put("weaknesses", jsonStrings(match.weaknesses))
        put("recommendations", jsonStrings(match.recommendations))
        put("explanation", match.explanation)
        match.cappedBy?.let { put("cappedBy", it) }
        put("isProbability", false)
    }

    fun documentJson(document: CareerDocument, includeContent: Boolean = false): JsonObject = buildJsonObject {
        put("id", document.id); put("kind", document.kind.name); put("title", document.title)
        put("version", document.version); put("createdAtMs", document.createdAtMs)
        if (document.jobId.isNotBlank()) put("jobId", document.jobId)
        put("facts", document.factIds.size)
        if (includeContent) put("content", document.content)
    }

    fun applicationJson(app: ApplicationRecord): JsonObject = buildJsonObject {
        put("id", app.id); put("jobId", app.jobId); put("role", app.role); put("company", app.company)
        put("status", app.status.name); put("createdAtMs", app.createdAtMs); put("updatedAtMs", app.updatedAtMs)
        app.submittedAtMs?.let { put("submittedAtMs", it) }
        app.interviewAtMs?.let { put("interviewAtMs", it) }
        app.followUpAtMs?.let { put("followUpAtMs", it) }
        putIf("confirmationReference", app.confirmationReference)
        put("verified", app.verification != null)
        put("approved", app.approval?.approvedByUser == true)
        put("documents", jsonStrings(app.documents))
        put("events", JsonArray(app.events.takeLast(20).map { e -> buildJsonObject {
            put("atMs", e.atMs); e.from?.let { put("from", it.name) }; put("to", e.to.name)
            putIf("note", e.note); putIf("evidence", e.evidence)
        } }))
        if (app.status != com.wangzan101.mika.core.career.ApplicationStatus.Submitted) {
            put("canSubmitOnlyByUser", true)
        }
    }
}
