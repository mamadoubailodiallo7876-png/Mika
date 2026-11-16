package com.wangzan101.mika.core.career

import java.io.File
import java.time.YearMonth

class CareerException(message: String) : Exception(message)

data class JobIntakeResult(
    val added: Boolean,
    val job: JobPosting,
    val duplicateOf: String? = null,
    val warnings: List<String> = emptyList(),
)

data class PreparedApplication(val application: ApplicationRecord, val documents: List<CareerDocument>)

data class SavedDraft(val document: CareerDocument, val warnings: List<String>)

/**
 * The one place that mutates career data. The AI tools and the Compose screen both go through it,
 * so the same validation, evidence rules and audit trail apply to both. In particular, only the UI
 * calls [recordSubmission]: no model-facing tool can mark an application as submitted.
 */
class CareerService(
    val store: CareerStore,
    private val clock: () -> Long = { store.now() },
    private val month: () -> YearMonth = { YearMonth.now() },
) {
    fun data(): CareerData = store.snapshot()

    fun now(): Long = clock()

    fun profile(): CandidateProfile = data().profile

    fun saveProfile(profile: CandidateProfile): CareerData {
        val issues = ProfileQuality.validate(profile).filter { it.severity == IssueSeverity.Error }
        if (issues.isNotEmpty()) throw CareerException(issues.joinToString("; ") { it.message })
        return store.saveProfile(profile).also { store.audit("profile.update", outcome = "ok") }
    }

    fun importProfileText(text: String, fileName: String): ProfileImportResult =
        ProfileImporter.fromText(text, fileName, store::newId, clock(), month())

    fun mergeImportedProfile(imported: CandidateProfile): CareerData {
        val merged = ProfileImporter.merge(profile(), imported, clock())
        return store.saveProfile(merged).also { store.audit("profile.import.merge", outcome = "${imported.proposedFactCount()} proposals") }
    }

    fun confirmImportedFacts(): CareerData =
        store.confirmImportedFacts().also { store.audit("profile.confirm_imported", outcome = "ok") }

    fun addJob(extracted: ExtractedJob, mergeDuplicate: Boolean = true): JobIntakeResult {
        val current = data()
        val duplicate = JobDedupe.findDuplicate(extracted.job, current.jobs)
        if (duplicate != null) {
            if (mergeDuplicate) {
                store.update { d ->
                    d.copy(jobs = d.jobs.map { if (it.id == duplicate.existing.id) JobDedupe.merge(it, extracted.job) else it })
                }
                store.audit("job.merge_duplicate", duplicate.existing.id, duplicate.reason)
            }
            return JobIntakeResult(false, duplicate.existing, duplicate.existing.id, extracted.uncertain + duplicate.reason)
        }
        val job = extracted.job.copy(fingerprint = JobDedupe.fingerprint(extracted.job))
        store.update { it.copy(jobs = listOf(job) + it.jobs) }
        store.audit("job.add", job.id)
        return JobIntakeResult(true, job, warnings = extracted.uncertain + job.suspicious)
    }

    fun match(jobId: String): JobMatch {
        val job = data().jobs.firstOrNull { it.id == jobId } ?: throw CareerException("Unknown job: $jobId")
        return JobMatcher.match(profile(), job, profile().preferences, month())
    }

    fun rank(limit: Int = 10): List<Pair<JobPosting, JobMatch>> =
        JobMatcher.rank(profile(), data().jobs, limit, month())

    fun searchJobs(query: String): List<JobPosting> = JobSearch.search(data().jobs, query)

    fun setJobStatus(jobId: String, status: JobStatus): JobPosting {
        val updated = store.update { d ->
            d.copy(jobs = d.jobs.map { if (it.id == jobId) it.copy(status = status) else it })
        }
        val job = updated.jobs.firstOrNull { it.id == jobId } ?: throw CareerException("Unknown job: $jobId")
        store.audit("job.status", jobId, status.name)
        return job
    }

    fun removeJob(jobId: String): CareerData = store.update { d -> d.copy(jobs = d.jobs.filterNot { it.id == jobId }) }
        .also { store.audit("job.remove", jobId) }

    fun generateResume(jobId: String = "", tailored: Boolean = false): CareerDocument {
        val profile = profile()
        requireUsable(profile)
        val job = if (jobId.isNotBlank() || tailored) {
            data().jobs.firstOrNull { it.id == jobId } ?: throw CareerException("Unknown job: $jobId")
        } else null
        val generated = if (job != null) DocumentEngine.tailorCv(profile, job, now = month())
        else DocumentEngine.generateCv(profile, now = month())
        return saveGenerated(generated, job?.id.orEmpty())
    }

    fun generateCoverLetter(jobId: String, tone: CoverLetterTone = CoverLetterTone.Professional): CareerDocument {
        val profile = profile()
        requireUsable(profile)
        val job = data().jobs.firstOrNull { it.id == jobId } ?: throw CareerException("Unknown job: $jobId")
        return saveGenerated(DocumentEngine.coverLetterDraft(profile, job, tone, month()), job.id)
    }

    fun saveDraft(
        kind: CareerDocumentKind,
        title: String,
        content: String,
        jobId: String = "",
    ): SavedDraft {
        if (content.isBlank()) throw CareerException("The document is empty.")
        if (content.length > 200_000) throw CareerException("The document is too long.")
        val check = DocumentEngine.reviewDraft(content, profile(), month())
        if (!check.ok) throw CareerException("Truthfulness check failed: ${check.problems.joinToString("; ")}")
        if (jobId.isNotBlank() && data().jobs.none { it.id == jobId }) throw CareerException("Unknown job: $jobId")
        val doc = saveGenerated(GeneratedDocument(kind, title.trim().ifBlank { "Untitled document" }, content.trim(), profile().allFactIds()), jobId)
        return SavedDraft(doc, check.warnings)
    }

    fun createApplication(jobId: String): ApplicationRecord {
        val current = data()
        val job = current.jobs.firstOrNull { it.id == jobId } ?: throw CareerException("Unknown job: $jobId")
        current.applications.firstOrNull { it.jobId == jobId && it.status !in setOf(ApplicationStatus.Closed, ApplicationStatus.Withdrawn) }
            ?.let { return it }
        val app = ApplicationWorkflow.newApplication(
            id = store.newId("app"), role = job.title, company = job.company, jobId = job.id, jobUrl = job.applicationUrl.ifBlank { job.url },
            nowMs = clock(),
        )
        persist(app)
        store.audit("application.create", app.id, job.id)
        return app
    }

    fun application(id: String): ApplicationRecord =
        data().applications.firstOrNull { it.id == id } ?: throw CareerException("Unknown application: $id")

    fun prepareApplication(applicationId: String, includeCoverLetter: Boolean = true): PreparedApplication {
        val current = data()
        val app = application(applicationId)
        val job = current.jobs.firstOrNull { it.id == app.jobId }
        val profile = current.profile
        requireUsable(profile)
        val resume = if (job != null) saveGenerated(DocumentEngine.tailorCv(profile, job, now = month()), job.id)
        else saveGenerated(DocumentEngine.generateCv(profile, now = month()), "")
        val cover = if (includeCoverLetter && job != null) saveGenerated(DocumentEngine.coverLetterDraft(profile, job, now = month()), job.id) else null
        val docs = listOfNotNull(resume, cover)
        val change = ApplicationWorkflow.prepare(app, docs.map { it.id }, clock())
        val updated = when (change) {
            is ApplicationChange.Changed -> change.application
            is ApplicationChange.Rejected -> throw CareerException(change.reason)
        }
        persist(updated)
        store.audit("application.prepare", updated.id, docs.size.toString())
        return PreparedApplication(updated, docs)
    }

    fun review(applicationId: String, eligibilityConfirmed: Boolean = false): SubmissionReadiness {
        val current = data()
        val app = application(applicationId)
        val job = current.jobs.firstOrNull { it.id == app.jobId }
        return ApplicationReadiness.review(app, job, current.documents, current.profile, eligibilityConfirmed)
    }

    /**
     * Moves a prepared application to AwaitingApproval. This is the last step the agent can take;
     * the actual submission is recorded only by the user's action in the Career screen.
     */
    fun requestSubmissionApproval(applicationId: String): ApplicationRecord {
        val app = application(applicationId)
        val change = when (app.status) {
            ApplicationStatus.Preparing -> ApplicationWorkflow.requestApproval(app, clock())
            ApplicationStatus.Shortlisted, ApplicationStatus.Discovered -> {
                val prepared = ApplicationWorkflow.prepare(app, app.documents, clock())
                val ready = (prepared as? ApplicationChange.Changed)?.application
                    ?: throw CareerException((prepared as ApplicationChange.Rejected).reason)
                ApplicationWorkflow.requestApproval(ready, clock())
            }
            ApplicationStatus.AwaitingApproval -> return app
            else -> throw CareerException("Cannot request approval from ${app.status}")
        }
        val updated = when (change) {
            is ApplicationChange.Changed -> change.application
            is ApplicationChange.Rejected -> throw CareerException(change.reason)
        }
        persist(updated)
        store.audit("application.request_approval", updated.id, updated.status.name)
        return updated
    }

    /**
     * Records a submission the user completed and explicitly approved. [approvedByUser] comes from
     * a UI confirmation, never from a model argument. A confirmation reference or message is required.
     */
    fun recordSubmission(
        applicationId: String,
        approvedByUser: Boolean,
        confirmationReference: String,
        confirmationText: String,
        applicationUrl: String = "",
        note: String = "",
    ): ApplicationRecord {
        val app = application(applicationId)
        val approval = ApplicationApproval(
            approvedByUser = approvedByUser, approvedAtMs = clock(),
            scope = "final submission", note = note.trim(),
        )
        val evidence = SubmissionEvidence(
            confirmationReference = confirmationReference.trim(),
            confirmationText = confirmationText.trim(),
            applicationUrl = applicationUrl.trim(),
            recordedAtMs = clock(),
        )
        val change = ApplicationWorkflow.submit(app, approval, evidence, clock())
        val updated = when (change) {
            is ApplicationChange.Changed -> change.application
            is ApplicationChange.Rejected -> throw CareerException(change.reason)
        }
        persist(updated)
        store.audit("application.submit.recorded_by_user", updated.id, updated.status.name)
        return updated
    }

    fun verifySubmission(applicationId: String, reference: String, source: String, evidenceText: String): ApplicationRecord {
        val app = application(applicationId)
        val change = ApplicationWorkflow.verify(app, VerificationEvidence(reference, source, evidenceText), clock())
        val updated = when (change) {
            is ApplicationChange.Changed -> change.application
            is ApplicationChange.Rejected -> throw CareerException(change.reason)
        }
        persist(updated)
        store.audit("application.verify", updated.id, updated.status.name)
        return updated
    }

    fun transition(applicationId: String, to: ApplicationStatus, note: String = ""): ApplicationRecord {
        val app = application(applicationId)
        val change = ApplicationWorkflow.transition(app, to, clock(), note = note)
        val updated = when (change) {
            is ApplicationChange.Changed -> change.application
            is ApplicationChange.Rejected -> throw CareerException(change.reason)
        }
        persist(updated)
        store.audit("application.transition", updated.id, to.name)
        return updated
    }

    fun recordInterview(applicationId: String, atMs: Long, note: String = ""): ApplicationRecord {
        val change = ApplicationWorkflow.interview(application(applicationId), atMs, clock(), note = note)
        val updated = when (change) {
            is ApplicationChange.Changed -> change.application
            is ApplicationChange.Rejected -> throw CareerException(change.reason)
        }
        persist(updated)
        store.audit("application.interview", updated.id, "scheduled")
        return updated
    }

    fun setFollowUp(applicationId: String, atMs: Long?): ApplicationRecord {
        val change = ApplicationWorkflow.setFollowUp(application(applicationId), atMs, clock())
        val updated = when (change) {
            is ApplicationChange.Changed -> change.application
            is ApplicationChange.Rejected -> throw CareerException(change.reason)
        }
        persist(updated)
        store.audit("application.follow_up", updated.id, if (atMs == null) "cleared" else "set")
        return updated
    }

    fun export(documentId: String, format: DocumentFormat): File {
        val doc = store.document(documentId) ?: throw CareerException("Unknown document: $documentId")
        val bytes = DocumentExporter.bytes(doc.title, doc.content, format)
        val file = store.writeExport(DocumentExporter.suggestedFileName(doc.title, format), bytes)
        store.audit("document.export", doc.id, format.name)
        return file
    }

    fun exportProfileJson(): String {
        store.audit("profile.export")
        return store.profileJson()
    }

    fun importProfileJson(text: String): CandidateProfile {
        val profile = store.readProfileJson(text).getOrElse { throw CareerException("Could not read the profile backup: ${it.message}") }
        store.saveProfile(profile)
        store.audit("profile.import_json")
        return profile
    }

    fun deleteDocument(documentId: String): CareerData = store.update { d -> d.copy(documents = d.documents.filterNot { it.id == documentId }) }
        .also { store.audit("document.delete", documentId) }

    fun deleteAllDocuments(): CareerData = store.update { d -> d.copy(documents = emptyList()) }
        .also { store.audit("document.delete_all") }

    fun eraseEverything() {
        store.audit("career.erase_all")
        store.eraseEverything()
    }

    fun dashboard(nowMs: Long = clock()): CareerDashboard = CareerOverview.build(data(), nowMs, month())

    fun document(id: String): CareerDocument? = store.document(id)

    fun documents(): List<CareerDocument> = data().documents

    private fun saveGenerated(generated: GeneratedDocument, jobId: String): CareerDocument {
        val now = clock()
        val doc = store.update { d ->
            val version = (d.documents.filter { it.kind == generated.kind && (jobId.isBlank() || it.jobId == jobId) }
                .maxOfOrNull { it.version } ?: 0) + 1
            val document = CareerDocument(
                id = store.newId("doc"), kind = generated.kind, title = generated.title, jobId = jobId,
                content = generated.content, createdAtMs = now, updatedAtMs = now, version = version,
                factIds = generated.factIds,
            )
            d.copy(documents = listOf(document) + d.documents)
        }
        val created = doc.documents.first()
        store.audit("document.generate", created.id, created.kind.name)
        return created
    }

    private fun persist(app: ApplicationRecord) {
        store.update { d ->
            d.copy(applications = if (d.applications.any { it.id == app.id }) d.applications.map { if (it.id == app.id) app else it }
            else listOf(app) + d.applications)
        }
    }

    private fun requireUsable(profile: CandidateProfile) {
        val hasSomething = profile.confirmedExperience().isNotEmpty() || profile.confirmedEducation().isNotEmpty() ||
            profile.confirmedSkills().isNotEmpty() || profile.fullName.isNotBlank()
        if (!hasSomething) throw CareerException("Add confirmed profile facts before generating a document.")
    }
}
