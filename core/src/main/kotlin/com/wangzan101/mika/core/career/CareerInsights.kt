package com.wangzan101.mika.core.career

import java.time.YearMonth

data class InterviewPlan(
    val jobId: String,
    val role: String,
    val company: String,
    val technicalQuestions: List<String>,
    val competencyQuestions: List<String>,
    val gapQuestions: List<String>,
    val questionsForRecruiter: List<String>,
    val researchChecklist: List<String>,
    val starFramework: String,
    val brief: String,
)

data class PracticeFeedback(val strengths: List<String>, val improvements: List<String>, val starCoverage: Int)

/**
 * Deterministic interview preparation. It turns the listing and the candidate's confirmed profile
 * into relevant practice material; it does not invent company facts and it never coaches the
 * candidate to claim experience they do not have.
 */
object InterviewPrep {
    fun plan(profile: CandidateProfile, job: JobPosting, match: JobMatch? = null, now: YearMonth = YearMonth.now()): InterviewPlan {
        val relevant = JobMatcher.requiredSkills(profile, job)
        val mine = profile.confirmedSkills().map { SkillVocabulary.canonical(it.name) }.toSet()
        val matched = relevant.filter { it in mine }.map(SkillVocabulary::display)
        val gaps = (match?.missingSkills ?: relevant.filterNot { it in mine }.map(SkillVocabulary::display)).distinct()
        val years = CareerDates.experienceYears(profile, now)

        val technical = relevant.take(8).map { canonical ->
            val skill = SkillVocabulary.display(canonical)
            if (canonical in mine) "Walk me through a project where you used $skill. What decisions did you make, and what would you change now?"
            else "$skill is named in this role. Describe any adjacent experience you have and how you would get up to speed honestly."
        }
        val competency = buildList {
            add("Tell me about a time you delivered a result under a tight deadline. What was your specific contribution?")
            add("Describe a disagreement with a colleague or stakeholder and how you resolved it.")
            add("Tell me about a mistake you made and what changed in how you work afterwards.")
            if (job.requirements.any { Regex("(?i)lead|mentor|manage").containsMatchIn(it) }) {
                add("Describe how you have led or mentored others. What did you change about your approach?")
            }
            if (job.requirements.any { Regex("(?i)cross[- ]functional|stakeholder|team").containsMatchIn(it) }) {
                add("How have you worked across functions to ship something? Who depended on you?")
            }
            if (job.remote == RemotePreference.Remote) add("How do you keep a remote team aligned and unblocked?")
        }
        val gapQuestions = gaps.take(6).map {
            "Prepare an honest answer about $it: what you have actually done nearby, what you are learning, and how you would close the gap in the first 90 days."
        }
        val research = buildList {
            add("Read the employer's own careers page and the original posting again; note the exact team and location.")
            add("Find one recent, verifiable product or company update and prepare a question about it.")
            add("Map the stated requirements to two concrete examples from your confirmed experience.")
            add("Check the interview format, time zone and any accessibility needs with the recruiter.")
            add("Look up the published salary range or market range; do not assume one.")
        }
        val star = "Situation: the context and constraint. Task: what you were responsible for. Action: what you personally did, step by step. Result: the outcome, with a verified metric if one exists."
        val brief = buildString {
            appendLine("Interview brief: ${job.title.ifBlank { "role" }}${job.company.takeIf { it.isNotBlank() }?.let { " at $it" }.orEmpty()}")
            appendLine("Confirmed fit: ${match?.score ?: "not scored"}/100. This is a preparation aid, not an outcome prediction.")
            if (matched.isNotEmpty()) appendLine("Strongest confirmed areas: ${matched.take(6).joinToString()}.")
            if (gaps.isNotEmpty()) appendLine("Gaps to prepare for honestly: ${gaps.take(6).joinToString()}.")
            if (years >= 1) appendLine("Confirmed experience: ${"%.1f".format(years)} years across the roles on your resume.")
            appendLine("Framework: $star")
        }.trimEnd()
        return InterviewPlan(
            jobId = job.id, role = job.title, company = job.company,
            technicalQuestions = technical,
            competencyQuestions = competency,
            gapQuestions = gapQuestions,
            questionsForRecruiter = listOf(
                "What does success look like in the first 90 days?",
                "How does the team make decisions and review work?",
                "What are the biggest constraints the team is facing right now?",
                "How do you handle growth and feedback for this role?",
                "What is the rest of the hiring process and the expected timeline?",
            ),
            researchChecklist = research,
            starFramework = star,
            brief = brief,
        )
    }

    /**
     * Structural practice feedback. It checks whether an answer contains the STAR elements and
     * concrete language. It cannot verify truth, so it explicitly asks the candidate to keep claims
     * to what actually happened.
     */
    fun reviewAnswer(answer: String): PracticeFeedback {
        val text = answer.trim()
        val lower = text.lowercase()
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val situation = Regex("(?i)\\b(when|while|at the time|during|in 20\\d\\d|last year|on a project|the team was)\\b").containsMatchIn(lower)
        val task = Regex("(?i)\\b(my (?:role|job|task|responsibility)|i was asked|i needed to|responsible for|goal was)\\b").containsMatchIn(lower)
        val action = Regex("(?i)\\b(i (?:built|led|designed|analysed|analyzed|wrote|tested|shipped|organised|organized|negotiated|debugged|migrated|automated|presented|coordinated|implemented))\\b").containsMatchIn(lower)
        val result = Regex("(?i)\\b(as a result|which (?:led|resulted)|the outcome|we (?:shipped|launched|reduced|increased)|it (?:reduced|increased|improved)|saved|cut|grew|reduced|increased|improved|delivered|launched|shipped)\\b").containsMatchIn(lower)
        val coverage = listOf(situation, task, action, result).count { it }
        val strengths = buildList {
            if (situation) add("You set the scene.")
            if (task) add("Your responsibility is explicit.")
            if (action) add("You describe actions you personally took.")
            if (result) add("You state an outcome.")
            if (words in 90..320) add("The length fits a spoken answer.")
        }
        val improvements = buildList {
            if (!situation) add("Add one sentence of context: where, when, and the constraint.")
            if (!task) add("Say what you specifically owned, not only what the team did.")
            if (!action) add("Use first-person action verbs for the steps you took.")
            if (!result) add("End with the result; use a verified number only if you have one.")
            if (words < 60) add("The answer is short; add the action steps and the result.")
            if (words > 350) add("Tighten the answer to the decisions and outcomes that matter.")
            if (Regex("(?i)\\b(everyone|always|never|obviously|basically)\\b").containsMatchIn(lower)) {
                add("Replace absolute or filler wording with concrete details.")
            }
            add("Keep every claim to what actually happened; do not invent metrics for practice.")
        }
        return PracticeFeedback(strengths, improvements, coverage)
    }
}

data class FollowUpItem(val application: ApplicationRecord, val atMs: Long)

data class CareerDashboard(
    val profilePercent: Int,
    val missingProfileItems: List<String>,
    val unconfirmedFacts: Int,
    val savedJobs: Int,
    val activeJobs: Int,
    val expiredJobs: Int,
    val flaggedJobs: Int,
    val shortlistedJobs: Int,
    val applications: Int,
    val activeApplications: Int,
    val awaitingApproval: Int,
    val interviews: Int,
    val offers: Int,
    val followUps: List<FollowUpItem>,
    val topMatches: List<Pair<JobPosting, JobMatch>>,
    val documents: Int,
    val nextActions: List<String>,
    val warnings: List<String>,
)

object CareerOverview {
    fun build(data: CareerData, nowMs: Long = System.currentTimeMillis(), now: YearMonth = YearMonth.now()): CareerDashboard {
        val score = ProfileQuality.score(data.profile, now)
        val stats = ApplicationAnalytics.stats(data.applications)
        val jobStats = JobSearch.stats(data.jobs, now)
        val followUps = ApplicationAnalytics.upcomingFollowUps(data.applications, nowMs)
            .mapNotNull { app -> app.followUpAtMs?.let { FollowUpItem(app, it) } }
        val ranked = JobMatcher.rank(data.profile, data.jobs, limit = 3, now = now)
        val next = buildList {
            addAll(ProfileQuality.nextActions(data.profile, 3))
            if (stats.awaitingApproval > 0) add("Review ${stats.awaitingApproval} application(s) waiting for your approval")
            if (followUps.isNotEmpty()) add("Follow up on ${followUps.first().application.company.ifBlank { followUps.first().application.role }}")
            if (data.jobs.any { it.suspicious.isNotEmpty() }) add("Review listings with safety flags before applying")
        }.take(6)
        val warnings = buildList {
            if (data.profile.proposedFactCount() > 0) {
                add("${data.profile.proposedFactCount()} imported fact(s) are unconfirmed and excluded from documents")
            }
            if (jobStats.flagged > 0) add("${jobStats.flagged} saved listing(s) have safety flags")
            if (jobStats.expired > 0) add("${jobStats.expired} saved listing(s) are past their deadline")
            if (stats.awaitingApproval > 0) add("${stats.awaitingApproval} application(s) need explicit approval before submission")
        }
        return CareerDashboard(
            profilePercent = score.percent,
            missingProfileItems = score.missing.take(8),
            unconfirmedFacts = data.profile.proposedFactCount(),
            savedJobs = jobStats.total,
            activeJobs = jobStats.active,
            expiredJobs = jobStats.expired,
            flaggedJobs = jobStats.flagged,
            shortlistedJobs = jobStats.shortlisted,
            applications = stats.total,
            activeApplications = stats.active,
            awaitingApproval = stats.awaitingApproval,
            interviews = stats.interviews,
            offers = stats.offers,
            followUps = followUps,
            topMatches = ranked,
            documents = data.documents.size,
            nextActions = next,
            warnings = warnings,
        )
    }
}
