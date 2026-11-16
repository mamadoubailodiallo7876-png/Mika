package com.wangzan101.mika.core.career

import java.util.UUID

sealed interface ApplicationChange {
    data class Changed(val application: ApplicationRecord) : ApplicationChange
    data class Rejected(val reason: String) : ApplicationChange
}

data class VerificationEvidence(
    val reference: String,
    val source: String,
    val text: String,
)

data class ReadinessCheck(val label: String, val passed: Boolean, val detail: String = "")

data class SubmissionReadiness(val ready: Boolean, val blockers: List<String>, val checks: List<ReadinessCheck>)

/**
 * The application state machine. Two actions are deliberately separated from ordinary status
 * changes:
 *
 * - [submit] is the only way to reach Submitted. It requires an explicit user approval and a
 *   confirmation reference or message captured from the employer.
 * - [verify] is the only way to reach UnderReview from Submitted. It requires text (a status page
 *   or email) that actually contains the confirmation reference.
 *
 * A click, a navigation, or an agent's claim is never enough.
 */
object ApplicationWorkflow {
    val allowed: Map<ApplicationStatus, Set<ApplicationStatus>> = mapOf(
        ApplicationStatus.Discovered to setOf(ApplicationStatus.Shortlisted, ApplicationStatus.Withdrawn, ApplicationStatus.Closed),
        ApplicationStatus.Shortlisted to setOf(ApplicationStatus.Preparing, ApplicationStatus.Withdrawn, ApplicationStatus.Closed),
        ApplicationStatus.Preparing to setOf(ApplicationStatus.AwaitingApproval, ApplicationStatus.Withdrawn, ApplicationStatus.Closed),
        ApplicationStatus.AwaitingApproval to setOf(ApplicationStatus.Preparing, ApplicationStatus.Withdrawn, ApplicationStatus.Closed),
        ApplicationStatus.Submitted to setOf(
            ApplicationStatus.Interview, ApplicationStatus.Rejected, ApplicationStatus.Withdrawn, ApplicationStatus.Closed,
        ),
        ApplicationStatus.UnderReview to setOf(
            ApplicationStatus.Interview, ApplicationStatus.Offer, ApplicationStatus.Rejected, ApplicationStatus.Withdrawn, ApplicationStatus.Closed,
        ),
        ApplicationStatus.Interview to setOf(ApplicationStatus.Offer, ApplicationStatus.Rejected, ApplicationStatus.Withdrawn, ApplicationStatus.Closed),
        ApplicationStatus.Offer to setOf(ApplicationStatus.Withdrawn, ApplicationStatus.Closed),
        ApplicationStatus.Rejected to setOf(ApplicationStatus.Closed),
        ApplicationStatus.Withdrawn to setOf(ApplicationStatus.Closed),
        ApplicationStatus.Closed to emptySet(),
    )

    fun allowedFrom(status: ApplicationStatus): Set<ApplicationStatus> = allowed[status].orEmpty()

    fun canTransition(from: ApplicationStatus, to: ApplicationStatus): Boolean = to in allowedFrom(from)

    fun newApplication(
        id: String,
        role: String,
        company: String,
        jobId: String = "",
        jobUrl: String = "",
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
    ): ApplicationRecord = ApplicationRecord(
        id = id, jobId = jobId, role = role, company = company, jobUrl = jobUrl,
        status = ApplicationStatus.Discovered, createdAtMs = nowMs, updatedAtMs = nowMs,
        events = listOf(ApplicationEvent(eventId, nowMs, null, ApplicationStatus.Discovered, "Application created")),
    )

    /** Ordinary transitions. Submitted and UnderReview have dedicated functions below. */
    fun transition(
        application: ApplicationRecord,
        to: ApplicationStatus,
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
        note: String = "",
    ): ApplicationChange {
        if (to == ApplicationStatus.Submitted) {
            return ApplicationChange.Rejected("submission needs explicit approval and confirmation evidence; use the submission action")
        }
        if (to == ApplicationStatus.UnderReview) {
            return ApplicationChange.Rejected("under review is set only by verification with the employer's confirmation reference")
        }
        if (to in setOf(ApplicationStatus.Interview, ApplicationStatus.Offer) && application.verification == null) {
            return ApplicationChange.Rejected("verify the application receipt before recording an interview or offer")
        }
        if (!canTransition(application.status, to)) {
            return ApplicationChange.Rejected("cannot move from ${application.status} to $to")
        }
        val updated = application.copy(
            status = to,
            updatedAtMs = nowMs,
            events = application.events + ApplicationEvent(eventId, nowMs, application.status, to, note),
        )
        return ApplicationChange.Changed(updated)
    }

    fun prepare(
        application: ApplicationRecord,
        documentIds: List<String>,
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
    ): ApplicationChange {
        if (application.status == ApplicationStatus.Discovered) {
            val shortlist = transition(application, ApplicationStatus.Shortlisted, nowMs, eventId, "Shortlisted")
            if (shortlist !is ApplicationChange.Changed) return shortlist
            return prepare(shortlist.application, documentIds, nowMs, "evt-${UUID.randomUUID().toString().take(8)}")
        }
        if (application.status == ApplicationStatus.AwaitingApproval) {
            val back = transition(application, ApplicationStatus.Preparing, nowMs, eventId, "Changes requested before approval")
            if (back !is ApplicationChange.Changed) return back
            return ApplicationChange.Changed(back.application.copy(documents = mergeIds(back.application.documents, documentIds)))
        }
        if (application.status != ApplicationStatus.Shortlisted) {
            return ApplicationChange.Rejected("preparation starts from Discovered, Shortlisted or AwaitingApproval")
        }
        val next = transition(application, ApplicationStatus.Preparing, nowMs, eventId, "Preparing documents")
        return if (next is ApplicationChange.Changed) {
            ApplicationChange.Changed(next.application.copy(documents = mergeIds(next.application.documents, documentIds)))
        } else next
    }

    fun requestApproval(
        application: ApplicationRecord,
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
        note: String = "",
    ): ApplicationChange {
        if (application.status != ApplicationStatus.Preparing) {
            return ApplicationChange.Rejected("only an application in Preparing can be sent for approval")
        }
        return transition(application, ApplicationStatus.AwaitingApproval, nowMs, eventId, note.ifBlank { "Waiting for the user's final approval" })
    }

    fun submit(
        application: ApplicationRecord,
        approval: ApplicationApproval,
        evidence: SubmissionEvidence,
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
    ): ApplicationChange {
        if (application.status != ApplicationStatus.AwaitingApproval) {
            return ApplicationChange.Rejected("user approval must be requested before submission")
        }
        if (!approval.approvedByUser) return ApplicationChange.Rejected("the user has not approved this submission")
        if (approval.approvedAtMs <= 0 || approval.approvedAtMs > nowMs) {
            return ApplicationChange.Rejected("the approval timestamp is missing or in the future")
        }
        if (evidence.confirmationReference.isBlank() && evidence.confirmationText.isBlank()) {
            return ApplicationChange.Rejected(
                "record the employer's confirmation reference or the confirmation message shown after sending; a click alone is not evidence",
            )
        }
        if (evidence.recordedAtMs <= 0) return ApplicationChange.Rejected("the evidence timestamp is missing")
        val reference = evidence.confirmationReference.trim().ifBlank { extractReference(evidence.confirmationText) }
        if (reference.isBlank()) {
            return ApplicationChange.Rejected("no confirmation reference could be read from the confirmation text")
        }
        val updated = application.copy(
            status = ApplicationStatus.Submitted,
            submittedAtMs = nowMs,
            updatedAtMs = nowMs,
            confirmationReference = reference,
            approval = approval.copy(note = Validators.bounded(approval.note, 500)),
            followUpAtMs = nowMs + FOLLOW_UP_MS,
            events = application.events + ApplicationEvent(
                eventId, nowMs, application.status, ApplicationStatus.Submitted,
                note = "Submitted after explicit user approval", evidence = Validators.bounded(evidence.confirmationText, 1000),
            ),
        )
        return ApplicationChange.Changed(updated)
    }

    fun verify(
        application: ApplicationRecord,
        evidence: VerificationEvidence,
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
    ): ApplicationChange {
        if (application.status !in setOf(ApplicationStatus.Submitted, ApplicationStatus.UnderReview)) {
            return ApplicationChange.Rejected("only a submitted application can be verified")
        }
        val reference = evidence.reference.trim().ifBlank { application.confirmationReference.trim() }
        if (reference.isBlank()) return ApplicationChange.Rejected("no confirmation reference is recorded for this application")
        val haystack = evidence.text.lowercase()
        if (!haystack.contains(reference.lowercase())) {
            return ApplicationChange.Rejected("the verification text does not contain the confirmation reference $reference")
        }
        val verified = application.copy(
            verification = SubmissionVerification(nowMs, reference, Validators.bounded(evidence.source, 120), Validators.bounded(evidence.text, 2000)),
            updatedAtMs = nowMs,
            events = application.events + ApplicationEvent(
                eventId, nowMs, application.status, application.status,
                note = "Receipt verified", evidence = Validators.bounded(evidence.text, 1000),
            ),
        )
        if (application.status == ApplicationStatus.Submitted) {
            return ApplicationChange.Changed(
                verified.copy(
                    status = ApplicationStatus.UnderReview,
                    events = verified.events + ApplicationEvent(
                        "evt-${UUID.randomUUID().toString().take(8)}", nowMs,
                        ApplicationStatus.Submitted, ApplicationStatus.UnderReview, "Employer confirmed receipt",
                    ),
                ),
            )
        }
        return ApplicationChange.Changed(verified)
    }

    fun interview(
        application: ApplicationRecord,
        atMs: Long,
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
        note: String = "",
    ): ApplicationChange {
        if (atMs <= nowMs) return ApplicationChange.Rejected("the interview time must be in the future")
        if (application.verification == null) {
            return ApplicationChange.Rejected("verify the application receipt before recording an interview")
        }
        if (!canTransition(application.status, ApplicationStatus.Interview)) {
            return ApplicationChange.Rejected("cannot move from ${application.status} to Interview")
        }
        val moved = transition(application, ApplicationStatus.Interview, nowMs, eventId, note.ifBlank { "Interview scheduled" })
        return if (moved is ApplicationChange.Changed) {
            ApplicationChange.Changed(moved.application.copy(interviewAtMs = atMs))
        } else moved
    }

    fun setFollowUp(
        application: ApplicationRecord,
        atMs: Long?,
        nowMs: Long,
        eventId: String = "evt-${UUID.randomUUID().toString().take(8)}",
    ): ApplicationChange {
        if (application.status in setOf(ApplicationStatus.Closed, ApplicationStatus.Withdrawn)) {
            return ApplicationChange.Rejected("closed applications have no follow-up")
        }
        return ApplicationChange.Changed(
            application.copy(
                followUpAtMs = atMs,
                updatedAtMs = nowMs,
                events = application.events + ApplicationEvent(
                    eventId, nowMs, application.status, application.status,
                    if (atMs == null) "Follow-up reminder cleared" else "Follow-up reminder updated",
                ),
            ),
        )
    }

    fun attachDocuments(application: ApplicationRecord, documentIds: List<String>, nowMs: Long): ApplicationRecord =
        application.copy(documents = mergeIds(application.documents, documentIds), updatedAtMs = nowMs)

    private fun mergeIds(a: List<String>, b: List<String>): List<String> = (a + b).map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun extractReference(text: String): String {
        val explicit = Regex("(?i)(?:reference|ref(?:erence)?\\s*(?:number|no\\.?|id)|confirmation\\s*(?:number|id|code)|application\\s*(?:number|id))\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9-]{3,30})")
            .find(text)?.groupValues?.get(1)
        if (explicit != null) return explicit
        return Regex("\\b[A-Z]{1,6}[-]?\\d{4,12}\\b").find(text)?.value.orEmpty()
    }

    const val FOLLOW_UP_MS = 7L * 24 * 60 * 60 * 1000
}

data class ApplicationStats(
    val total: Int,
    val active: Int,
    val submitted: Int,
    val interviews: Int,
    val offers: Int,
    val rejected: Int,
    val awaitingApproval: Int,
    val responseRatePercent: Int,
)

object ApplicationAnalytics {
    fun stats(applications: List<ApplicationRecord>): ApplicationStats {
        val submitted = applications.count { it.submittedAtMs != null }
        val responses = applications.count { it.status in setOf(ApplicationStatus.Interview, ApplicationStatus.Offer, ApplicationStatus.Rejected, ApplicationStatus.UnderReview) }
        return ApplicationStats(
            total = applications.size,
            active = applications.count { it.status !in setOf(ApplicationStatus.Closed, ApplicationStatus.Withdrawn, ApplicationStatus.Rejected) },
            submitted = submitted,
            interviews = applications.count { it.status == ApplicationStatus.Interview },
            offers = applications.count { it.status == ApplicationStatus.Offer },
            rejected = applications.count { it.status == ApplicationStatus.Rejected },
            awaitingApproval = applications.count { it.status == ApplicationStatus.AwaitingApproval },
            // Responses divided by submitted applications, shown as a count-based ratio, not a prediction.
            responseRatePercent = if (submitted == 0) 0 else (100.0 * responses / submitted).toInt().coerceIn(0, 100),
        )
    }

    fun upcomingFollowUps(applications: List<ApplicationRecord>, nowMs: Long, horizonMs: Long = 14L * 24 * 60 * 60 * 1000): List<ApplicationRecord> =
        applications.filter { app ->
            app.followUpAtMs?.let { it in nowMs..(nowMs + horizonMs) } == true &&
                app.status !in setOf(ApplicationStatus.Closed, ApplicationStatus.Withdrawn, ApplicationStatus.Rejected)
        }.sortedBy { it.followUpAtMs }
}

/**
 * Pre-submission review. It reports blockers rather than silently filling gaps, and it never
 * asserts eligibility on the candidate's behalf.
 */
object ApplicationReadiness {
    private val ELIGIBILITY = Regex("(?i)\\b(?:must be (?:at least )?\\d{2}|minimum age|authori[sz]ed to work|work permit|visa|security clearance|background check)\\b")

    fun review(
        application: ApplicationRecord,
        job: JobPosting?,
        documents: List<CareerDocument>,
        profile: CandidateProfile,
        eligibilityConfirmed: Boolean = false,
    ): SubmissionReadiness {
        val checks = mutableListOf<ReadinessCheck>()
        val blockers = mutableListOf<String>()
        fun check(label: String, passed: Boolean, detail: String = "", blocker: Boolean = true) {
            checks += ReadinessCheck(label, passed, detail)
            if (!passed && blocker) blockers += if (detail.isBlank()) label else "$label: $detail"
        }
        check("Profile name and contact", profile.fullName.isNotBlank() && profile.email.isNotBlank(),
            "Name and email are needed on most application forms")
        check("Application in awaiting approval", application.status == ApplicationStatus.AwaitingApproval,
            "Current status is ${application.status}")
        val linked = documents.filter { it.id in application.documents }
        check("A resume is attached", linked.any { it.kind == CareerDocumentKind.Resume },
            "Generate or link a resume before submitting")
        check("Documents reviewed", application.documents.isNotEmpty(), "No document is linked to this application")
        check("No unconfirmed facts", profile.proposedFactCount() == 0,
            "${profile.proposedFactCount()} imported fact(s) still need confirmation", blocker = true)
        val text = "${job?.description.orEmpty()} ${job?.requirements?.joinToString().orEmpty()}"
        if (ELIGIBILITY.containsMatchIn(text)) {
            check("Eligibility confirmed by the user", eligibilityConfirmed,
                "The listing states an eligibility or age requirement; confirm it yourself before applying")
        }
        check("Submission window", job?.deadline?.let { !CareerDates.isExpired(it) } ?: true,
            "The stated deadline has passed", blocker = true)
        if (job?.suspicious?.isNotEmpty() == true) {
            checks += ReadinessCheck("Safety flags reviewed", false, job.suspicious.joinToString("; "))
            blockers += "Safety flags were not acknowledged: ${job.suspicious.joinToString("; ")}"
        }
        return SubmissionReadiness(blockers.isEmpty(), blockers.distinct(), checks)
    }
}
