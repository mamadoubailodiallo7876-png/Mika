package com.wangzan101.mika.core.career

import kotlinx.serialization.Serializable

/**
 * Career data model. Everything here is candidate-owned data: it is stored only on the device,
 * never sent anywhere by this module, and can be erased from the Career privacy controls.
 *
 * Truthfulness rule: a fact is usable in a generated document only when [FactSource] is
 * [FactSource.UserProvided] or the entry has been explicitly confirmed by the user
 * ([confirmed] == true). Imported and agent-drafted facts are proposals until then.
 */
const val CAREER_SCHEMA = 1

/** Where a fact came from. Imported and drafted facts are never treated as verified by default. */
@Serializable
enum class FactSource { UserProvided, ImportedDocument, AgentDrafted }

@Serializable
data class ProfileLinks(
    val github: String = "",
    val linkedin: String = "",
    val portfolio: String = "",
    val website: String = "",
)

@Serializable
data class EducationEntry(
    val id: String,
    val institution: String,
    val degree: String = "",
    val fieldOfStudy: String = "",
    /** Free-form start date as the candidate wrote it (for example 2018 or 2018-09). */
    val start: String = "",
    val end: String = "",
    val grade: String = "",
    val details: String = "",
    val source: FactSource = FactSource.UserProvided,
    val confirmed: Boolean = true,
)

@Serializable
data class QualificationEntry(
    val id: String,
    val name: String,
    val issuer: String = "",
    val year: String = "",
    val details: String = "",
    val source: FactSource = FactSource.UserProvided,
    val confirmed: Boolean = true,
)

@Serializable
data class ExperienceEntry(
    val id: String,
    val company: String,
    val title: String,
    val location: String = "",
    val start: String = "",
    val end: String = "",
    val current: Boolean = false,
    val summary: String = "",
    val bullets: List<String> = emptyList(),
    val source: FactSource = FactSource.UserProvided,
    val confirmed: Boolean = true,
)

@Serializable
data class SkillEntry(
    val id: String,
    val name: String,
    val level: String = "",
    val years: Int? = null,
    val source: FactSource = FactSource.UserProvided,
    val confirmed: Boolean = true,
)

@Serializable
data class CertificationEntry(
    val id: String,
    val name: String,
    val issuer: String = "",
    val year: String = "",
    val credentialId: String = "",
    val source: FactSource = FactSource.UserProvided,
    val confirmed: Boolean = true,
)

@Serializable
data class ProjectEntry(
    val id: String,
    val name: String,
    val url: String = "",
    val description: String = "",
    val bullets: List<String> = emptyList(),
    val source: FactSource = FactSource.UserProvided,
    val confirmed: Boolean = true,
)

@Serializable
data class LanguageEntry(
    val id: String,
    val name: String,
    val proficiency: String = "",
    val source: FactSource = FactSource.UserProvided,
    val confirmed: Boolean = true,
)

@Serializable
enum class RemotePreference { Unspecified, Remote, Hybrid, OnSite }

@Serializable
enum class EmploymentType { Unspecified, FullTime, PartTime, Contract, Internship, Temporary, Volunteer }

/** Weights used by [JobMatcher]. They are normalised, so they do not have to sum to 100. */
@Serializable
data class MatchWeights(
    val skills: Int = 40,
    val experience: Int = 20,
    val education: Int = 10,
    val location: Int = 10,
    val preferences: Int = 10,
    val salary: Int = 10,
)

@Serializable
data class CareerPreferences(
    val targetRoles: List<String> = emptyList(),
    val preferredLocations: List<String> = emptyList(),
    val remote: RemotePreference = RemotePreference.Unspecified,
    val employmentTypes: List<EmploymentType> = emptyList(),
    val minSalary: Int? = null,
    val currency: String = "",
    val seniority: String = "",
    val industries: List<String> = emptyList(),
    val mustHaveSkills: List<String> = emptyList(),
    val dealBreakers: List<String> = emptyList(),
    val weights: MatchWeights = MatchWeights(),
)

@Serializable
data class CandidateProfile(
    val fullName: String = "",
    val headline: String = "",
    val summary: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    val links: ProfileLinks = ProfileLinks(),
    /** Work authorisation / eligibility facts exactly as the candidate stated them. */
    val workAuthorization: String = "",
    val education: List<EducationEntry> = emptyList(),
    val qualifications: List<QualificationEntry> = emptyList(),
    val experience: List<ExperienceEntry> = emptyList(),
    val skills: List<SkillEntry> = emptyList(),
    val certifications: List<CertificationEntry> = emptyList(),
    val projects: List<ProjectEntry> = emptyList(),
    val languages: List<LanguageEntry> = emptyList(),
    val preferences: CareerPreferences = CareerPreferences(),
    val updatedAtMs: Long = 0,
) {
    fun allFactIds(): List<String> = buildList {
        education.forEach { add("education:${it.id}") }
        qualifications.forEach { add("qualification:${it.id}") }
        experience.forEach { add("experience:${it.id}") }
        skills.forEach { add("skill:${it.id}") }
        certifications.forEach { add("certification:${it.id}") }
        projects.forEach { add("project:${it.id}") }
        languages.forEach { add("language:${it.id}") }
    }
}

@Serializable
enum class JobStatus { Active, Shortlisted, Expired, Closed, Removed }

@Serializable
data class JobPosting(
    val id: String,
    val title: String,
    val company: String = "",
    val description: String = "",
    val location: String = "",
    val remote: RemotePreference = RemotePreference.Unspecified,
    val salaryText: String = "",
    val employmentType: EmploymentType = EmploymentType.Unspecified,
    val requirements: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val experienceYears: Int? = null,
    val educationRequirement: String = "",
    /** ISO date (YYYY-MM-DD) when the listing closes, as stated by the source. */
    val deadline: String = "",
    val source: String = "",
    val url: String = "",
    val applicationUrl: String = "",
    val discoveredAtMs: Long = 0,
    val status: JobStatus = JobStatus.Active,
    /** Human-readable safety flags; a flag never silently rejects a listing. */
    val suspicious: List<String> = emptyList(),
    val fingerprint: String = "",
    val notes: String = "",
)

@Serializable
enum class ApplicationStatus {
    Discovered,
    Shortlisted,
    Preparing,
    AwaitingApproval,
    Submitted,
    UnderReview,
    Interview,
    Offer,
    Rejected,
    Withdrawn,
    Closed,
}

@Serializable
data class ApplicationApproval(
    val approvedByUser: Boolean,
    val approvedAtMs: Long,
    val scope: String = "final submission",
    val note: String = "",
)

@Serializable
data class SubmissionEvidence(
    val confirmationReference: String = "",
    val confirmationText: String = "",
    val applicationUrl: String = "",
    val recordedAtMs: Long = 0,
)

@Serializable
data class SubmissionVerification(
    val verifiedAtMs: Long,
    val reference: String,
    val source: String,
    val matchedText: String,
)

@Serializable
data class ApplicationEvent(
    val id: String,
    val atMs: Long,
    val from: ApplicationStatus?,
    val to: ApplicationStatus,
    val note: String = "",
    val evidence: String = "",
)

@Serializable
data class ApplicationRecord(
    val id: String,
    val jobId: String = "",
    val role: String,
    val company: String = "",
    val jobUrl: String = "",
    val status: ApplicationStatus = ApplicationStatus.Discovered,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    val submittedAtMs: Long? = null,
    val interviewAtMs: Long? = null,
    val followUpAtMs: Long? = null,
    val confirmationReference: String = "",
    val verification: SubmissionVerification? = null,
    val approval: ApplicationApproval? = null,
    val documents: List<String> = emptyList(),
    val events: List<ApplicationEvent> = emptyList(),
    val notes: String = "",
)

@Serializable
enum class CareerDocumentKind { Resume, CoverLetter }

@Serializable
enum class DocumentFormat { Pdf, Docx, Txt }

@Serializable
data class CareerDocument(
    val id: String,
    val kind: CareerDocumentKind,
    val title: String,
    val jobId: String = "",
    val content: String,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    val version: Int = 1,
    val template: String = "ats-classic",
    /** Fact ids the document was generated from, for auditability. */
    val factIds: List<String> = emptyList(),
)

@Serializable
data class WebsiteAdapter(
    val id: String,
    val name: String,
    val hosts: List<String>,
    val capabilities: List<AdapterCapability>,
    val notes: String = "",
    val termsNote: String = "",
    val custom: Boolean = false,
)

@Serializable
enum class AdapterCapability {
    JobSearch, OfficialApi, AccountRegistration, ApplicationForm, ResumeUpload, CoverLetter, StatusTracking,
}

@Serializable
data class CareerData(
    val schemaVersion: Int = CAREER_SCHEMA,
    val profile: CandidateProfile = CandidateProfile(),
    val jobs: List<JobPosting> = emptyList(),
    val applications: List<ApplicationRecord> = emptyList(),
    val documents: List<CareerDocument> = emptyList(),
    val adapters: List<WebsiteAdapter> = emptyList(),
    val updatedAtMs: Long = 0,
)

/** A generated document plus the facts it was built from. */
data class GeneratedDocument(
    val kind: CareerDocumentKind,
    val title: String,
    val content: String,
    val factIds: List<String>,
)

/** A truthfulness review of arbitrary text written by the agent before it is saved as a document. */
data class TruthCheck(val ok: Boolean, val problems: List<String>, val warnings: List<String>)
