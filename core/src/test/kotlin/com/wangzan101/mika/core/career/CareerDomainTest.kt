package com.wangzan101.mika.core.career

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.YearMonth

class CareerDomainTest {
    private val now = YearMonth.of(2025, 6)
    private val nowMs = 1_750_000_000_000L

    private fun profile(): CandidateProfile = CandidateProfile(
        fullName = "Jane Doe",
        headline = "Senior Android Engineer",
        email = "jane@example.com",
        phone = "+44 7700 900123",
        location = "London, UK",
        links = ProfileLinks(github = "https://github.com/janedoe", linkedin = "https://linkedin.com/in/janedoe"),
        experience = listOf(
            ExperienceEntry(
                id = "exp1", company = "Acme Ltd", title = "Software Engineer", start = "2018-01", end = "2021-01",
                bullets = listOf("Built a payments screen used by 20,000 customers"),
            ),
            ExperienceEntry(
                id = "exp2", company = "Beta Corp", title = "Senior Android Engineer", start = "2021-01", current = true,
                bullets = listOf("Led a team of four engineers"),
            ),
        ),
        education = listOf(EducationEntry("edu1", "University of Example", "BSc", "Computer Science", "2014", "2018")),
        skills = listOf(SkillEntry("s1", "Kotlin"), SkillEntry("s2", "Android"), SkillEntry("s3", "Jetpack Compose")),
        certifications = listOf(CertificationEntry("c1", "Associate Android Developer", "Google", "2020")),
        languages = listOf(LanguageEntry("l1", "English", "Native")),
        preferences = CareerPreferences(
            targetRoles = listOf("Android Engineer"), preferredLocations = listOf("London"),
            remote = RemotePreference.Remote, employmentTypes = listOf(EmploymentType.FullTime),
            minSalary = 100_000, currency = "USD", mustHaveSkills = listOf("Kotlin"),
        ),
        updatedAtMs = nowMs,
    )

    private fun job(): JobPosting = JobPosting(
        id = "job1", title = "Senior Android Engineer", company = "Gamma",
        description = "Build Android applications with Kotlin and Compose.",
        location = "Remote", remote = RemotePreference.Remote, salaryText = "USD 120,000 - 150,000",
        employmentType = EmploymentType.FullTime, requirements = listOf("3+ years of Android experience"),
        skills = listOf("Kotlin", "Android", "Jetpack Compose"), experienceYears = 3,
        deadline = "2026-01-31", source = "greenhouse", url = "https://boards.greenhouse.io/gamma/jobs/1",
        discoveredAtMs = nowMs, fingerprint = "fp",
    )

    private fun sampleCv(): String = """
        Jane Doe
        Senior Android Engineer
        jane@example.com | +44 7700 900123 | London, UK
        linkedin.com/in/janedoe
        github.com/janedoe

        SUMMARY
        Android engineer who builds reliable mobile products.

        EXPERIENCE
        Software Engineer
        Acme Ltd (2018 - 2021)
        - Built a payments screen used by 20,000 customers
        - Reduced crash-free regression by 40%

        Senior Android Engineer
        Beta Corp (2021 - Present)
        - Led a team of four engineers
        - Shipped Compose migration

        EDUCATION
        BSc Computer Science
        University of Example (2014 - 2018)

        SKILLS
        Kotlin, Android, Jetpack Compose, Coroutines

        CERTIFICATIONS
        Google Associate Android Developer (2020)

        LANGUAGES
        English (Native), French (B1)
    """.trimIndent()

    @Test fun profileScoreCountsOnlyConfirmedFacts() {
        val base = profile()
        val confirmed = ProfileQuality.score(base, now)
        val withProposal = base.copy(experience = base.experience + ExperienceEntry(
            id = "draft", company = "Draft Co", title = "Draft role", source = FactSource.ImportedDocument, confirmed = false,
        ))
        assertEquals(confirmed.percent, ProfileQuality.score(withProposal, now).percent)
        assertTrue(ProfileQuality.validate(withProposal).any { it.field == "profile.proposals" })
        assertTrue(confirmed.percent >= 80)
    }

    @Test fun validatorRejectsBadContactAndDates() {
        val bad = profile().copy(email = "not-an-email", experience = listOf(
            ExperienceEntry("e", "Co", "Dev", start = "2024-01", end = "2023-01"),
        ))
        val issues = ProfileQuality.validate(bad)
        assertTrue(issues.any { it.field == "email" && it.severity == IssueSeverity.Error })
        assertTrue(issues.any { it.field.startsWith("experience.e.end") })
    }

    @Test fun truthGuardBlocksInventedClaims() {
        val p = profile()
        val check = TruthGuard.check(
            """
            Jane Doe has 12 years of experience with Kotlin, holds an MBA, and can be reached at other@example.com.
            Skills: Kotlin, Rocket Surgery
            """.trimIndent(),
            p, now,
        )
        assertFalse(check.ok)
        assertTrue(check.problems.any { it.contains("12 years") })
        assertTrue(check.problems.any { it.contains("MBA") })
        assertTrue(check.problems.any { it.contains("other@example.com") })
        assertTrue(check.problems.any { it.contains("Rocket Surgery") })
    }

    @Test fun generatedCvAndCoverLetterPassTruthCheck() {
        val p = profile()
        val cv = DocumentEngine.generateCv(p, now = now)
        val cvCheck = TruthGuard.check(cv.content, p, now)
        assertTrue(cvCheck.problems.joinToString(), cvCheck.ok)
        assertTrue(cv.content.contains("Acme Ltd"))
        assertFalse("unconfirmed facts must not leak into a CV", cv.content.contains("Draft Co"))

        val letter = DocumentEngine.coverLetterDraft(p, job(), now = now)
        assertTrue(TruthGuard.check(letter.content, p, now).problems.joinToString(), TruthGuard.check(letter.content, p, now).ok)
        assertTrue(letter.content.contains("Senior Android Engineer"))
    }

    @Test fun tailoredCvReordersSkillsWithoutInventing() {
        val p = profile().copy(skills = listOf(SkillEntry("s1", "Rust"), SkillEntry("s2", "Kotlin"), SkillEntry("s3", "Compose")))
        val doc = DocumentEngine.tailorCv(p, job(), now = now)
        val skillsLine = doc.content.lines().dropWhile { it != "SKILLS" }.getOrNull(1).orEmpty()
        assertTrue(skillsLine.indexOf("Kotlin") < skillsLine.indexOf("Rust"))
        assertFalse(doc.content.contains("GAPS NOT ADDED"))
        assertFalse(doc.content.contains("Rust\nRust"))
    }

    @Test fun matchingExplainsAndCapsDealBreakers() {
        val p = profile()
        val good = JobMatcher.match(p, job(), p.preferences, now)
        assertTrue(good.score >= 70)
        assertTrue(good.explanation.contains("not a probability"))
        assertTrue(good.matchedSkills.contains("Kotlin"))
        assertTrue(good.missingSkills.isEmpty())

        val risky = job().copy(description = job().description + " On-call rotation is mandatory.")
        val capped = JobMatcher.match(p.copy(preferences = p.preferences.copy(dealBreakers = listOf("on-call"))), risky, p.preferences.copy(dealBreakers = listOf("on-call")), now)
        assertTrue(capped.score <= 30)
        assertNotNull(capped.cappedBy)

        val mustHave = JobMatcher.match(
            p.copy(preferences = p.preferences.copy(mustHaveSkills = listOf("Kubernetes"))),
            job().copy(skills = listOf("Kotlin", "Kubernetes"), description = ""),
            p.preferences.copy(mustHaveSkills = listOf("Kubernetes")), now,
        )
        assertTrue(mustHave.score <= 45)
        assertTrue(mustHave.missingSkills.any { it.equals("kubernetes", true) })
    }

    @Test fun salaryAndExperienceAreReadFromFacts() {
        val salary = SalaryParser.parse("USD 120,000 - 150,000 per year")
        assertNotNull(salary)
        assertEquals("USD", salary!!.currency)
        assertEquals(120_000, salary.min)
        assertEquals(150_000, salary.max)
        val monthly = SalaryParser.parse("EUR 4,000 per month")
        assertNotNull(monthly)
        assertEquals(48_000, monthly!!.min)
        assertEquals(7.08, CareerDates.experienceYears(profile(), YearMonth.of(2025, 1)), 0.15)
    }

    @Test fun dedupeIgnoresTrackingParamsAndMergesFacts() {
        val existing = job()
        val incoming = job().copy(id = "job2", url = "https://boards.greenhouse.io/gamma/jobs/1?utm_source=newsletter&utm_medium=email")
        assertEquals(JobDedupe.normalizeUrl(existing.url), JobDedupe.normalizeUrl(incoming.url))
        val duplicate = JobDedupe.findDuplicate(incoming, listOf(existing))
        assertNotNull(duplicate)
        val merged = JobDedupe.merge(existing, incoming.copy(description = "New detail"))
        assertEquals("Gamma", merged.company)
        assertTrue(merged.description.contains("Build Android"))
    }

    @Test fun suspiciousListingsAreFlaggedNotRejected() {
        val scam = job().copy(company = "", description = "Unpaid internship. A refundable deposit and bank account number are required.", salaryText = "USD 500 per hour")
        val flags = JobDedupe.suspiciousFlags(scam)
        assertTrue(flags.any { it.contains("fee") || it.contains("deposit") })
        assertTrue(flags.any { it.contains("financial") })
        assertTrue(flags.any { it.contains("unpaid") })
        assertTrue(flags.any { it.contains("hourly") })
        assertTrue(JobDedupe.refreshStatuses(listOf(job().copy(deadline = "2020-01-01")), now).single().status == JobStatus.Expired)
    }

    @Test fun jobExtractionKeepsUnknownsEmpty() {
        val extracted = JobExtractor.fromText("Acme is hiring a Backend Engineer. Requirements: Python and SQL.", id = "j", nowMs = nowMs, url = "https://acme.example/jobs/1")
        assertEquals("j", extracted.job.id)
        assertTrue(extracted.job.skills.contains("python"))
        assertTrue(extracted.job.company.isBlank())
        assertTrue(extracted.uncertain.contains("employer name"))
        assertEquals("https://acme.example/jobs/1", extracted.job.url)
    }

    @Test fun applicationCannotBeSubmittedWithoutApprovalAndEvidence() {
        val app = ApplicationWorkflow.newApplication("a1", "Senior Android Engineer", "Gamma", nowMs = nowMs)
        val preparing = (ApplicationWorkflow.prepare(app, listOf("doc1"), nowMs) as ApplicationChange.Changed).application
        assertEquals(ApplicationStatus.Preparing, preparing.status)
        val awaiting = (ApplicationWorkflow.requestApproval(preparing, nowMs) as ApplicationChange.Changed).application
        assertEquals(ApplicationStatus.AwaitingApproval, awaiting.status)

        val noApproval = ApplicationWorkflow.submit(
            awaiting, ApplicationApproval(false, nowMs), SubmissionEvidence(confirmationReference = "ABC-12345", recordedAtMs = nowMs), nowMs,
        )
        assertTrue(noApproval is ApplicationChange.Rejected)

        val noEvidence = ApplicationWorkflow.submit(
            awaiting, ApplicationApproval(true, nowMs), SubmissionEvidence(recordedAtMs = nowMs), nowMs,
        )
        assertTrue(noEvidence is ApplicationChange.Rejected)

        val submitted = (ApplicationWorkflow.submit(
            awaiting, ApplicationApproval(true, nowMs), SubmissionEvidence(confirmationReference = "ABC-12345", recordedAtMs = nowMs), nowMs,
        ) as ApplicationChange.Changed).application
        assertEquals(ApplicationStatus.Submitted, submitted.status)
        assertNotNull(submitted.submittedAtMs)
        assertTrue(submitted.followUpAtMs!! > nowMs)

        val wrong = ApplicationWorkflow.verify(submitted, VerificationEvidence("ABC-12345", "status page", "Nothing here"), nowMs)
        assertTrue(wrong is ApplicationChange.Rejected)
        val verified = (ApplicationWorkflow.verify(
            submitted, VerificationEvidence("ABC-12345", "status page", "Thanks. Your reference is ABC-12345."), nowMs,
        ) as ApplicationChange.Changed).application
        assertEquals(ApplicationStatus.UnderReview, verified.status)
        assertNotNull(verified.verification)

        val interview = ApplicationWorkflow.interview(verified, nowMs + 86_400_000, nowMs)
        assertTrue(interview is ApplicationChange.Changed)
        assertTrue(ApplicationWorkflow.transition(submitted, ApplicationStatus.Submitted, nowMs) is ApplicationChange.Rejected)
        assertTrue(ApplicationWorkflow.transition(submitted, ApplicationStatus.Interview, nowMs) is ApplicationChange.Rejected)
    }

    @Test fun formMappingFillsOnlyVerifiedFields() {
        val docs = listOf(
            CareerDocument("doc-resume", CareerDocumentKind.Resume, "Jane Doe — Resume", content = "cv", updatedAtMs = 5),
            CareerDocument("doc-cover", CareerDocumentKind.CoverLetter, "Cover", content = "letter", updatedAtMs = 6),
        )
        val fields = listOf(
            FormField("First name", required = true), FormField("Last name", required = true),
            FormField("Email address", required = true), FormField("Phone", required = true),
            FormField("Resume/CV", type = "file", required = true),
            FormField("Why do you want this role?", type = "textarea", required = true),
            FormField("Social Security Number", required = true),
            FormField("Company", required = true),
            FormField("LinkedIn profile"),
        )
        val analysis = FormFieldMapper.analyze(fields, profile(), docs, job(), WebsiteAdapters.forUrl("https://boards.greenhouse.io/gamma/jobs/1"))
        val map = analysis.mappings.associateBy { it.canonical }
        assertEquals("Jane", map["firstName"]?.value)
        assertEquals("Doe", map["lastName"]?.value)
        assertEquals("jane@example.com", map["email"]?.value)
        assertEquals("doc-resume", map["resume"]?.source?.substringAfterLast(' '))
        assertTrue(map["resume"]?.value?.contains("Resume") == true)
        assertTrue(analysis.sensitiveFields.contains("Social Security Number"))
        assertTrue(analysis.unresolvedRequired.any { it.contains("Social Security") })
        assertTrue(analysis.questionsForUser.any { it.contains("Why do you want") })
        assertTrue(analysis.unresolvedRequired.any { it == "Company" })
        assertEquals("greenhouse", analysis.adapter?.id)
        assertTrue(analysis.steps.any { it.contains("approval") })
    }

    @Test fun adaptersMatchHostsAndFallBackToGeneric() {
        assertEquals("greenhouse", WebsiteAdapters.forUrl("https://job-boards.greenhouse.io/acme/jobs/1")?.id)
        assertEquals("workday", WebsiteAdapters.forUrl("https://acme.wd1.myworkdayjobs.com/en-US/External")?.id)
        assertEquals(null, WebsiteAdapters.forUrl("https://example.com/jobs"))
        assertTrue(WebsiteAdapters.canonicalFields.contains("workAuthorization"))
    }

    @Test fun cvImportIsUnconfirmedAndMergeDoesNotOverwrite() {
        var n = 0
        val imported = ProfileImporter.fromText(sampleCv(), "cv.docx", { prefix -> "$prefix-${n++}" }, nowMs, now)
        assertEquals(2, imported.suggestions.experience.size)
        assertEquals("Software Engineer", imported.suggestions.experience.first { it.company == "Acme Ltd" }.title)
        assertEquals("Senior Android Engineer", imported.suggestions.experience.first { it.company == "Beta Corp" }.title)
        assertTrue(imported.suggestions.experience.all { !it.confirmed && it.source == FactSource.ImportedDocument })
        assertTrue(imported.suggestions.skills.size >= 3)
        assertTrue(imported.suggestions.education.isNotEmpty())
        assertEquals("jane@example.com", imported.suggestions.email)
        assertEquals("https://linkedin.com/in/janedoe", imported.suggestions.links.linkedin)
        assertTrue(imported.proposedCount > 0)
        assertTrue(imported.warnings.any { it.contains("unconfirmed") })

        val existing = profile().copy(fullName = "Existing Name")
        val merged = ProfileImporter.merge(existing, imported.suggestions, nowMs)
        assertEquals("Existing Name", merged.fullName)
        assertTrue(merged.experience.size >= existing.experience.size)
        assertTrue(merged.skills.any { it.name == "Coroutines" && !it.confirmed })
    }

    @Test fun storePersistsAtomicallyAndRecoversFromCorruption() {
        val dir = Files.createTempDirectory("career-store").toFile()
        var n = 0
        val store = CareerStore(dir, clock = { nowMs }, ids = { p -> "$p-${n++}" })
        store.saveProfile(profile())
        store.audit("profile.update")
        val reloaded = CareerStore(dir).snapshot()
        assertEquals("Jane Doe", reloaded.profile.fullName)
        assertTrue(File(dir, CareerStore.AUDIT).isFile)

        File(dir, CareerStore.FILE).writeText("{not json")
        val recovered = CareerStore(dir).snapshot()
        assertEquals("", recovered.profile.fullName)
        assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("career.corrupt-") })

        val cleanDir = Files.createTempDirectory("career-store-erase").toFile()
        val clean = CareerStore(cleanDir, clock = { nowMs }, ids = { p -> "$p-${n++}" })
        clean.saveProfile(profile())
        clean.writeExport("cv.pdf", byteArrayOf(1, 2, 3))
        clean.eraseEverything()
        assertEquals("", clean.snapshot().profile.fullName)
        assertTrue(clean.exports().isEmpty())
        assertFalse(File(cleanDir, CareerStore.AUDIT).exists())
    }

    @Test fun docxAndPdfExportsRoundTrip() {
        val cv = DocumentEngine.generateCv(profile(), now = now)
        val docx = DocumentExporter.bytes(cv.title, cv.content, DocumentFormat.Docx)
        assertTrue(docx.size > 500)
        assertEquals('P'.code.toByte(), docx[0])
        assertEquals('K'.code.toByte(), docx[1])
        val docxText = DocumentText.extract("cv.docx", docx)
        assertTrue(docxText.text.contains("JANE DOE"))
        assertTrue(docxText.text.contains("EXPERIENCE"))
        assertTrue(docxText.text.contains("Acme Ltd"))

        val pdf = DocumentExporter.bytes(cv.title, cv.content, DocumentFormat.Pdf)
        assertTrue(String(pdf.copyOf(5), Charsets.ISO_8859_1) == "%PDF-")
        val pdfText = DocumentText.extract("cv.pdf", pdf)
        assertTrue(pdfText.text, pdfText.text.contains("JANE DOE"))
        assertTrue(pdfText.text, pdfText.text.contains("EXPERIENCE"))
        assertTrue(pdfText.warnings.isEmpty())

        val txt = DocumentText.extract("cv.txt", cv.content.toByteArray())
        assertTrue(txt.text.contains("Beta Corp"))
    }

    @Test fun serviceGeneratesVersionsAndRequiresUserSubmission() {
        val dir = Files.createTempDirectory("career-service").toFile()
        var n = 0
        val store = CareerStore(dir, clock = { nowMs }, ids = { p -> "$p-${n++}" })
        val service = CareerService(store, clock = { nowMs }, month = { now })
        store.saveProfile(profile())
        val intake = service.addJob(ExtractedJob(job(), listOf("location is remote")))
        assertTrue(intake.added)
        val jobId = intake.job.id
        val first = service.generateResume(jobId, tailored = true)
        val second = service.generateResume(jobId, tailored = true)
        assertEquals(1, first.version)
        assertEquals(2, second.version)
        assertTrue(service.documents().size >= 2)

        val app = service.createApplication(jobId)
        val prepared = service.prepareApplication(app.id)
        assertEquals(ApplicationStatus.Preparing, prepared.application.status)
        assertTrue(prepared.documents.isNotEmpty())
        val awaiting = service.requestSubmissionApproval(app.id)
        assertEquals(ApplicationStatus.AwaitingApproval, awaiting.status)
        val submitted = service.recordSubmission(
            app.id, approvedByUser = true, confirmationReference = "GAMMA-7788",
            confirmationText = "Application received. Reference GAMMA-7788.", note = "approved in UI",
        )
        assertEquals(ApplicationStatus.Submitted, submitted.status)
        val verified = service.verifySubmission(app.id, "GAMMA-7788", "status page", "Reference GAMMA-7788: under review")
        assertEquals(ApplicationStatus.UnderReview, verified.status)
        assertTrue(store.auditLog().any { it.contains("application.submit.recorded_by_user") })

        val dashboard = service.dashboard(nowMs)
        assertTrue(dashboard.applications == 1)
        assertTrue(dashboard.profilePercent >= 80)
        assertTrue(dashboard.documents >= 2)
    }

    @Test fun draftSavingRejectsUntrueClaims() {
        val dir = Files.createTempDirectory("career-draft").toFile()
        val store = CareerStore(dir, clock = { nowMs })
        val service = CareerService(store, clock = { nowMs }, month = { now })
        store.saveProfile(profile())
        val ok = service.saveDraft(CareerDocumentKind.CoverLetter, "Letter", "Dear Hiring Team,\n\nI am applying for the Android Engineer role.\n\nSincerely,\nJane Doe")
        assertTrue(ok.document.version == 1)
        var rejected = false
        try {
            service.saveDraft(CareerDocumentKind.CoverLetter, "Bad", "I have 15 years of experience and an MBA.")
        } catch (_: CareerException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test fun interviewPrepUsesGapsAndPracticeFeedbackIsStructural() {
        val p = profile()
        val plan = InterviewPrep.plan(p, job().copy(skills = listOf("Kotlin", "Kubernetes")), JobMatcher.match(p, job().copy(skills = listOf("Kotlin", "Kubernetes")), p.preferences, now), now)
        assertTrue(plan.technicalQuestions.any { it.contains("Kubernetes") })
        assertTrue(plan.gapQuestions.any { it.contains("Kubernetes") })
        assertTrue(plan.researchChecklist.isNotEmpty())
        assertTrue(plan.brief.contains("not an outcome prediction"))

        val feedback = InterviewPrep.reviewAnswer("I built a login screen that reduced support tickets by 30%.")
        assertTrue(feedback.starCoverage >= 2)
        assertTrue(feedback.improvements.any { it.contains("context") || it.contains("result") })
    }

    @Test fun dashboardSummarisesRealCounts() {
        val data = CareerData(
            profile = profile(),
            jobs = listOf(job(), job().copy(id = "job2", company = "", suspicious = listOf("No employer name was provided."))),
            applications = listOf(
                ApplicationWorkflow.newApplication("a1", "Android Engineer", "Gamma", jobId = "job1", nowMs = nowMs),
                ApplicationWorkflow.newApplication("a2", "Android Engineer", "Delta", nowMs = nowMs)
                    .copy(status = ApplicationStatus.AwaitingApproval),
            ),
            documents = listOf(CareerDocument("d1", CareerDocumentKind.Resume, "CV", content = "cv")),
            updatedAtMs = nowMs,
        )
        val dashboard = CareerOverview.build(data, nowMs, now)
        assertEquals(2, dashboard.applications)
        assertEquals(1, dashboard.awaitingApproval)
        assertEquals(1, dashboard.flaggedJobs)
        assertTrue(dashboard.nextActions.any { it.contains("approval") })
        assertTrue(dashboard.topMatches.isNotEmpty())
    }
}
