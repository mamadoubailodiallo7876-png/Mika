package com.wangzan101.mika.ui

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.core.career.ApplicationRecord
import com.wangzan101.mika.core.career.ApplicationStatus
import com.wangzan101.mika.core.career.CandidateProfile
import com.wangzan101.mika.core.career.CareerDashboard
import com.wangzan101.mika.core.career.CareerDocument
import com.wangzan101.mika.core.career.CareerDocumentKind
import com.wangzan101.mika.core.career.CareerService
import com.wangzan101.mika.core.career.CareerStore
import com.wangzan101.mika.core.career.DocumentFormat
import com.wangzan101.mika.core.career.JobPosting
import com.wangzan101.mika.core.career.JobSearch
import com.wangzan101.mika.core.career.JobStatus
import com.wangzan101.mika.core.career.ProfileQuality
import com.wangzan101.mika.core.career.SkillEntry
import com.wangzan101.mika.core.career.proposedFactCount
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.FooterNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.SearchField
import com.wangzan101.mika.ui.components.SegmentedControl
import com.wangzan101.mika.ui.components.StateCard
import com.wangzan101.mika.ui.components.SubPage
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.components.ValueTone
import com.wangzan101.mika.ui.components.cardSurface
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAB_HOME = 0
private const val TAB_PROFILE = 1
private const val TAB_JOBS = 2
private const val TAB_APPLICATIONS = 3
private const val TAB_DOCUMENTS = 4

private object CareerCopy {
    const val TITLE = "Career"
    const val HERO_LABEL = "CAREER WORKSPACE"
    const val EMPTY_PROFILE = "Your career workspace is ready. Add your details once and every resume, match and application starts from the same verified facts."
    const val PRIVACY = "Career data stays on this device, encrypted with the Android Keystore. Only confirmed facts are used in documents. Jobby never sends an application for you."
    const val USER_SUBMITS = "You submit the final application. Jobby can prepare, review and record it only after you confirm the employer reference."
}

/**
 * The Career workspace: one on-device place for the profile, resumes, saved jobs, matching,
 * applications and interview preparation. It deliberately has no "auto submit" button; the final
 * submission is recorded by the user with employer evidence after they submit in the browser.
 */
@Composable
fun CareerScreen(context: Context, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val store = remember(context) { (context.applicationContext as MikaApp).career }
    val service = remember(store) { CareerService(store) }
    var data by remember { mutableStateOf(service.data()) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingProfile by remember { mutableStateOf(false) }
    var importingCv by remember { mutableStateOf(false) }
    var addingJob by remember { mutableStateOf(false) }
    var addingExperience by remember { mutableStateOf(false) }
    var addingEducation by remember { mutableStateOf(false) }
    var addingSkill by remember { mutableStateOf(false) }
    var selectedJobId by rememberSaveable { mutableStateOf("") }
    var selectedApplicationId by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf<ApplicationRecord?>(null) }
    var verifying by remember { mutableStateOf<ApplicationRecord?>(null) }
    var previewing by remember { mutableStateOf<CareerDocument?>(null) }
    var confirmingErase by remember { mutableStateOf(false) }

    fun refresh() { data = service.data() }
    fun run(block: () -> String?): Boolean = runCatching {
        val message = block()
        refresh()
        notice = message
        error = null
        true
    }.getOrElse {
        error = it.message ?: "Something went wrong."
        false
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }

    SubPage(CareerCopy.TITLE, onBack) {
        SegmentedControl(
            listOf("Home", "Profile", "Jobs", "Apps", "Docs"), tab, { tab = it },
            tags = listOf("career-tab-home", "career-tab-profile", "career-tab-jobs", "career-tab-apps", "career-tab-docs"),
        )
        error?.let { Text(it, Modifier.padding(top = 10.dp), style = MikaType.Caption, color = MikaTheme.colors.bad) }
        notice?.let { Text(it, Modifier.padding(top = 10.dp), style = MikaType.Caption, color = MikaTheme.colors.ok) }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            TAB_PROFILE -> ProfileTab(
                data = data,
                onEdit = { editingProfile = true },
                onImport = { importingCv = true },
                onExperience = { addingExperience = true },
                onEducation = { addingEducation = true },
                onSkill = { addingSkill = true },
                onConfirmImported = { run { service.confirmImportedFacts(); "Imported facts confirmed and included in documents." } },
                onErase = { confirmingErase = true },
            )
            TAB_JOBS -> JobsTab(
                data = data, service = service,
                selectedJobId = selectedJobId,
                onSelect = { selectedJobId = it },
                onAdd = { addingJob = true },
                onResume = { job -> run { previewing = service.generateResume(job.id, tailored = true); "Tailored resume generated." } },
                onCoverLetter = { job -> run { previewing = service.generateCoverLetter(job.id); "Cover letter generated." } },
                onPrepare = { job ->
                    run {
                        val app = service.createApplication(job.id)
                        service.prepareApplication(app.id, includeCoverLetter = true)
                        selectedApplicationId = app.id
                        tab = TAB_APPLICATIONS
                        "Application prepared. Review the documents, then request approval."
                    }
                },
                onRemove = { job -> run { service.removeJob(job.id); selectedJobId = ""; "Listing removed." } },
            )
            TAB_APPLICATIONS -> ApplicationsTab(
                data = data, service = service,
                selectedApplicationId = selectedApplicationId,
                onSelect = { selectedApplicationId = it },
                onPrepare = { app -> run { service.prepareApplication(app.id, true); "Documents prepared and linked." } },
                onApproval = { app -> run { service.requestSubmissionApproval(app.id); "Ready for your approval. Review the blockers, then submit in the browser." } },
                onRecord = { submitting = it },
                onVerify = { verifying = it },
                onTransition = { app, status -> run { service.transition(app.id, status, "Updated in Career"); "Application moved to ${status.label()}." } },
            )
            TAB_DOCUMENTS -> DocumentsTab(
                documents = data.documents,
                onPreview = { previewing = it },
                onExport = { doc, format -> run {
                    val file = service.export(doc.id, format)
                    val saved = publishToDownloads(context, file, mimeOf(format))
                    "Exported ${file.name} to Downloads/Jobby ($saved)."
                } },
            )
            else -> HomeTab(
                data = data, service = service,
                onTab = { tab = it },
                onImport = { importingCv = true },
                onAddJob = { addingJob = true },
                onEditProfile = { editingProfile = true },
                onSelectJob = { selectedJobId = it; tab = TAB_JOBS },
                onSelectApplication = { selectedApplicationId = it; tab = TAB_APPLICATIONS },
            )
        }
        FooterNote(CareerCopy.PRIVACY)
    }

    if (editingProfile) {
        ProfileEditDialog(
            profile = data.profile,
            onDismiss = { editingProfile = false },
            onSave = { updated ->
                if (run { service.saveProfile(updated); "Profile saved." }) editingProfile = false
            },
        )
    }
    if (importingCv) {
        ImportCvDialog(
            onDismiss = { importingCv = false },
            onImport = { text, fileName ->
                val ok = run {
                    val imported = service.importProfileText(text, fileName)
                    service.mergeImportedProfile(imported.suggestions)
                    "Found ${imported.proposedCount} proposal(s). Review them on the Profile tab, then confirm what is true."
                }
                if (ok) importingCv = false
                ok
            },
        )
    }
    if (addingJob) {
        AddJobDialog(
            service = service,
            onDismiss = { addingJob = false },
            onAdded = { intake ->
                if (intake.added) {
                    selectedJobId = intake.job.id
                    notice = if (intake.duplicateOf != null) "This listing was already saved; the existing record was reused." else "Listing saved."
                    error = null
                    refresh()
                } else {
                    error = "This listing is already saved."
                }
                addingJob = false
            },
            onError = { error = it },
        )
    }
    if (addingExperience) {
        AddExperienceDialog(
            store = store,
            onDismiss = { addingExperience = false },
            onSave = { entry -> run { service.saveProfile(data.profile.copy(experience = data.profile.experience + entry)); "Experience added." }; addingExperience = false },
        )
    }
    if (addingEducation) {
        AddEducationDialog(
            store = store,
            onDismiss = { addingEducation = false },
            onSave = { entry -> run { service.saveProfile(data.profile.copy(education = data.profile.education + entry)); "Education added." }; addingEducation = false },
        )
    }
    if (addingSkill) {
        AddSkillDialog(
            store = store,
            onDismiss = { addingSkill = false },
            onSave = { entry -> run { service.saveProfile(data.profile.copy(skills = data.profile.skills + entry)); "Skill added." }; addingSkill = false },
        )
    }
    submitting?.let { app ->
        SubmissionDialog(
            application = app,
            onDismiss = { submitting = null },
            onRecord = { reference, evidence, url ->
                run {
                    service.recordSubmission(
                        applicationId = app.id,
                        approvedByUser = true,
                        confirmationReference = reference,
                        confirmationText = evidence,
                        applicationUrl = url,
                    )
                    "Submission recorded from your confirmation. No application was sent by Jobby."
                }
                submitting = null
            },
        )
    }
    verifying?.let { app ->
        VerifyDialog(
            application = app,
            onDismiss = { verifying = null },
            onVerify = { reference, source, evidence ->
                run { service.verifySubmission(app.id, reference, source, evidence); "Receipt verified." }
                verifying = null
            },
        )
    }
    previewing?.let { doc ->
        DocumentPreviewDialog(document = doc, onDismiss = { previewing = null })
    }
    if (confirmingErase) {
        MikaDialog(
            onDismissRequest = { confirmingErase = false },
            title = { Text("Delete all career data?") },
            text = { Text("This removes the profile, documents, saved listings, applications and exports from this device. It cannot be undone. Existing Mika conversations are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    service.eraseEverything()
                    refresh()
                    confirmingErase = false
                    notice = "Career data erased."
                    error = null
                }) { Text("Delete everything", color = MikaTheme.colors.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmingErase = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HomeTab(
    data: com.wangzan101.mika.core.career.CareerData,
    service: CareerService,
    onTab: (Int) -> Unit,
    onImport: () -> Unit,
    onAddJob: () -> Unit,
    onEditProfile: () -> Unit,
    onSelectJob: (String) -> Unit,
    onSelectApplication: (String) -> Unit,
) {
    val dashboard = remember(data) { service.dashboard() }
    CareerHero(data.profile, dashboard, onEditProfile)
    if (dashboard.warnings.isNotEmpty()) {
        StateCard(MikaIcons.Exclaim, "Needs your attention", dashboard.warnings.first(), tone = TileTone.Warn)
    }
    GroupLabel("Quick actions")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MikaButton("Import CV", onImport, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
        MikaButton("Paste job", onAddJob, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MikaButton("Edit profile", onEditProfile, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
        MikaButton("All jobs", { onTab(TAB_JOBS) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CareerStat("Saved", dashboard.savedJobs, Modifier.weight(1f))
        CareerStat("Active", dashboard.activeApplications, Modifier.weight(1f))
        CareerStat("Interviews", dashboard.interviews, Modifier.weight(1f))
        CareerStat("Offers", dashboard.offers, Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    GroupLabel("Recommended for you")
    if (dashboard.topMatches.isEmpty()) {
        EmptyNote("Save a listing and add a few skills; matches appear here with an explanation, never a promised outcome.")
    } else {
        GroupCard {
            dashboard.topMatches.forEachIndexed { index, (job, match) ->
                ListRow(
                    MikaIcons.Chart, job.title.ifBlank { "Untitled role" },
                    subtitle = listOf(job.company, job.location).filter { it.isNotBlank() }.joinToString(" · "),
                    first = index == 0,
                    detail = match.missingSkills.take(3).takeIf { it.isNotEmpty() }?.joinToString(prefix = "Gaps: ") { it },
                    onClick = { onSelectJob(job.id) },
                    trailing = { MatchPill(match.score) },
                )
            }
        }
    }
    GroupLabel("Applications")
    if (data.applications.isEmpty()) {
        EmptyNote("No applications yet. Prepare one from a saved job when you are ready.")
    } else {
        GroupCard {
            data.applications.sortedByDescending { it.updatedAtMs }.take(4).forEachIndexed { index, app ->
                ListRow(
                    MikaIcons.Message, app.role.ifBlank { "Application" },
                    subtitle = app.company.ifBlank { "Company not recorded" },
                    first = index == 0,
                    detail = app.confirmationReference.takeIf { it.isNotBlank() }?.let { "Reference $it" },
                    onClick = { onSelectApplication(app.id) },
                    trailing = { StatusPill(app.status) },
                )
            }
        }
    }
    if (dashboard.followUps.isNotEmpty()) {
        GroupLabel("Follow-ups")
        GroupCard {
            dashboard.followUps.take(4).forEachIndexed { index, item ->
                ListRow(
                    MikaIcons.Clock, item.application.role,
                    subtitle = item.application.company.ifBlank { "Company not recorded" },
                    first = index == 0,
                    detail = date(item.atMs),
                    onClick = { onSelectApplication(item.application.id) },
                )
            }
        }
    }
    if (dashboard.nextActions.isNotEmpty()) {
        GroupLabel("Next best actions")
        GroupCard {
            dashboard.nextActions.forEachIndexed { index, action ->
                ListRow(MikaIcons.Check, action, first = index == 0)
            }
        }
    }
}

@Composable
private fun CareerHero(profile: CandidateProfile, dashboard: CareerDashboard, onEdit: () -> Unit) {
    val c = MikaTheme.colors
    MikaCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(46.dp).background(c.accentSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials(profile.fullName), style = MikaType.BodyStrong, color = c.accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(CareerCopy.HERO_LABEL, style = MikaType.Hint, color = c.ink3, fontWeight = FontWeight.Medium)
                    Text(profile.fullName.ifBlank { "Your career profile" }, style = MikaType.Title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${dashboard.profilePercent}%", style = MikaType.BodyStrong, color = c.accent)
            }
            Text(
                profile.headline.ifBlank { profile.preferences.targetRoles.firstOrNull().orEmpty() }
                    .ifBlank { CareerCopy.EMPTY_PROFILE },
                Modifier.padding(top = 12.dp), style = MikaType.Caption, color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            ProgressBar(dashboard.profilePercent, c.accent)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (dashboard.missingProfileItems.isEmpty()) "Profile complete" else "Add ${dashboard.missingProfileItems.first()}",
                    Modifier.weight(1f), style = MikaType.Hint, color = c.ink3,
                )
                MikaButton(if (profile.fullName.isBlank()) "Build profile" else "Improve profile", onEdit, kind = ButtonKind.Text, size = ButtonSize.Small)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileTab(
    data: com.wangzan101.mika.core.career.CareerData,
    onEdit: () -> Unit,
    onImport: () -> Unit,
    onExperience: () -> Unit,
    onEducation: () -> Unit,
    onSkill: () -> Unit,
    onConfirmImported: () -> Unit,
    onErase: () -> Unit,
) {
    val c = MikaTheme.colors
    val profile = data.profile
    val score = remember(profile) { ProfileQuality.score(profile) }
    GroupLabel("Profile")
    GroupCard {
        ListRow(
            MikaIcons.Edit, profile.fullName.ifBlank { "Add your name" },
            subtitle = listOf(profile.email, profile.phone, profile.location).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Contact details are needed for applications" },
            first = true,
            detail = profile.links.linkedin.takeIf { it.isNotBlank() }?.let { "LinkedIn connected" },
            onClick = onEdit,
            trailing = { RowValue("${score.percent}%", ValueTone.Ok) },
        )
        ListRow(MikaIcons.Download, "Import an existing CV", subtitle = "Paste text from PDF, DOCX or a plain CV; every imported fact stays unconfirmed until you check it.", onClick = onImport)
        ListRow(MikaIcons.Edit, "Edit profile", subtitle = "Name, headline, summary, contact details, target roles and salary preference.", onClick = onEdit)
    }

    if (profile.proposedFactCount() > 0) {
        StateCard(
            MikaIcons.Exclaim, "${profile.proposedFactCount()} imported fact(s) need review",
            "They are excluded from generated documents until you confirm them.", tone = TileTone.Warn,
        ) { MikaButton("Confirm all", onConfirmImported, kind = ButtonKind.Secondary, size = ButtonSize.Small) }
    }

    if (score.missing.isNotEmpty()) {
        GroupLabel("To complete")
        GroupCard {
            score.missing.forEachIndexed { index, item -> ListRow(MikaIcons.Plus, item, first = index == 0, onClick = onEdit) }
        }
    }

    GroupLabel("Experience")
    if (profile.experience.isEmpty()) {
        EmptyNote("No roles yet. Add only work you can verify.")
    } else {
        GroupCard {
            profile.experience.forEachIndexed { index, role ->
                ListRow(
                    MikaIcons.Book, role.title.ifBlank { "Role" },
                    subtitle = listOf(role.company, role.start, role.end.ifBlank { if (role.current) "Present" else "" }).filter { it.isNotBlank() }.joinToString(" · "),
                    first = index == 0,
                    detail = role.bullets.firstOrNull(),
                )
            }
        }
    }
    MikaButton("Add experience", onExperience, Modifier.fillMaxWidth(), ButtonKind.Tonal, ButtonSize.Medium)
    Spacer(Modifier.height(8.dp))

    GroupLabel("Education")
    if (profile.education.isEmpty()) {
        EmptyNote("No education recorded.")
    } else {
        GroupCard {
            profile.education.forEachIndexed { index, entry ->
                ListRow(
                    MikaIcons.Book, entry.institution.ifBlank { "Education" },
                    subtitle = listOf(entry.degree, entry.fieldOfStudy, entry.end).filter { it.isNotBlank() }.joinToString(" · "),
                    first = index == 0,
                )
            }
        }
    }
    MikaButton("Add education", onEducation, Modifier.fillMaxWidth(), ButtonKind.Tonal, ButtonSize.Medium)
    Spacer(Modifier.height(8.dp))

    GroupLabel("Skills")
    if (profile.skills.isEmpty()) {
        EmptyNote("Skills drive matching. Add the ones you can demonstrate.")
    } else {
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            profile.skills.forEach { skill -> CareerChip(skill.name + skill.level.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()) }
        }
    }
    MikaButton("Add skill", onSkill, Modifier.fillMaxWidth(), ButtonKind.Tonal, ButtonSize.Medium)

    if (profile.languages.isNotEmpty()) {
        GroupLabel("Languages")
        GroupCard {
            profile.languages.forEachIndexed { index, language ->
                ListRow(MikaIcons.Globe, language.name, subtitle = language.proficiency, first = index == 0)
            }
        }
    }
    if (profile.certifications.isNotEmpty() || profile.qualifications.isNotEmpty()) {
        GroupLabel("Certifications & qualifications")
        GroupCard {
            var first = true
            profile.certifications.forEach { cert ->
                ListRow(MikaIcons.Shield, cert.name, subtitle = listOf(cert.issuer, cert.year).filter { it.isNotBlank() }.joinToString(" · "), first = first)
                first = false
            }
            profile.qualifications.forEach { qual ->
                ListRow(MikaIcons.Book, qual.name, subtitle = listOf(qual.issuer, qual.year).filter { it.isNotBlank() }.joinToString(" · "), first = first)
                first = false
            }
        }
    }

    GroupLabel("Privacy")
    GroupCard {
        ListRow(
            MikaIcons.Trash, "Delete career data",
            subtitle = "Erase the profile, documents, listings, applications and exports from this device.", first = true,
            titleColor = c.bad, onClick = onErase,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun JobsTab(
    data: com.wangzan101.mika.core.career.CareerData,
    service: CareerService,
    selectedJobId: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onResume: (JobPosting) -> Unit,
    onCoverLetter: (JobPosting) -> Unit,
    onPrepare: (JobPosting) -> Unit,
    onRemove: (JobPosting) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // 每次按键都重排整份清单会掉帧:打字停下再搜,清空时立刻还原
    var applied by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query == applied) return@LaunchedEffect
        if (query.isNotBlank()) kotlinx.coroutines.delay(180)
        applied = query
    }
    val jobs = remember(data.jobs, applied) {
        val filtered = JobSearch.search(data.jobs, applied, includeClosed = true)
        if (applied.isBlank()) filtered.sortedByDescending { it.discoveredAtMs } else filtered
    }
    // 一次算好所有活跃职位的匹配分,别在每一行的组合里各查一次
    val scores = remember(data.jobs, data.profile) {
        data.jobs.filter { it.status == JobStatus.Active }
            .associate { it.id to runCatching { service.match(it.id).score }.getOrNull() }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(query, { query = it }, "Search saved jobs", Modifier.weight(1f))
        MikaButton("Add", onAdd, kind = ButtonKind.Tonal, size = ButtonSize.Small)
    }
    Spacer(Modifier.height(12.dp))
    val selected = jobs.firstOrNull { it.id == selectedJobId }
    if (selected != null) {
        JobDetail(selected, service, data.profile, onResume, onCoverLetter, onPrepare, onRemove, onClose = { onSelect("") })
    }
    GroupLabel("${jobs.size} saved listing(s)")
    if (jobs.isEmpty()) {
        EmptyNote("No saved listings. Paste a job description and Jobby extracts the fields, checks duplicates and flags suspicious details.")
    } else {
        GroupCard {
            jobs.forEachIndexed { index, job ->
                ListRow(
                    MikaIcons.Chart, job.title.ifBlank { "Untitled role" },
                    subtitle = listOf(job.company, job.location).filter { it.isNotBlank() }.joinToString(" · "),
                    first = index == 0,
                    detail = when {
                        job.suspicious.isNotEmpty() -> "Safety flag: ${job.suspicious.first()}"
                        job.status == JobStatus.Expired -> "Past the stated deadline"
                        job.salaryText.isNotBlank() -> job.salaryText
                        else -> null
                    },
                    detailColor = if (job.suspicious.isNotEmpty()) MikaTheme.colors.warn else Color.Unspecified,
                    onClick = { onSelect(job.id) },
                    trailing = { scores[job.id]?.let { MatchPill(it) } },
                )
            }
        }
    }
    FooterNote("Listings come from pages you choose to read or paste. Jobby does not bypass sign-in walls, CAPTCHAs or site terms.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobDetail(
    job: JobPosting,
    service: CareerService,
    profile: CandidateProfile,
    onResume: (JobPosting) -> Unit,
    onCoverLetter: (JobPosting) -> Unit,
    onPrepare: (JobPosting) -> Unit,
    onRemove: (JobPosting) -> Unit,
    onClose: () -> Unit,
) {
    val c = MikaTheme.colors
    val match = remember(job, profile) { runCatching { service.match(job.id) }.getOrNull() }
    MikaCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(job.title.ifBlank { "Untitled role" }, style = MikaType.BodyStrong, color = c.ink)
                    Text(listOf(job.company, job.location, job.employmentType.name).filter { it.isNotBlank() && it != "Unspecified" }.joinToString(" · "), style = MikaType.Caption, color = c.ink3)
                }
                match?.let { MatchPill(it.score) }
                TextButton(onClick = onClose) { Text("Close", color = c.ink2) }
            }
            if (match != null) {
                Text(match.explanation, Modifier.padding(top = 10.dp), style = MikaType.Caption, color = c.ink2)
                if (match.missingSkills.isNotEmpty()) {
                    Text("Missing: ${match.missingSkills.take(4).joinToString()}", Modifier.padding(top = 6.dp), style = MikaType.Hint, color = c.warn)
                }
            }
            if (job.suspicious.isNotEmpty()) {
                Text(job.suspicious.joinToString("\n"), Modifier.padding(top = 8.dp), style = MikaType.Hint, color = c.warn)
            }
            if (job.description.isNotBlank()) {
                Text(job.description.take(900), Modifier.padding(top = 10.dp), style = MikaType.Caption, color = c.ink2, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
            if (job.skills.isNotEmpty()) {
                FlowRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    job.skills.take(12).forEach { CareerChip(it) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MikaButton("Tailor resume", { onResume(job) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                MikaButton("Cover letter", { onCoverLetter(job) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
            }
            MikaButton("Prepare application", { onPrepare(job) }, Modifier.fillMaxWidth().padding(top = 8.dp), ButtonKind.Primary, ButtonSize.Medium)
            TextButton(onClick = { onRemove(job) }, Modifier.fillMaxWidth()) { Text("Remove listing", color = c.ink3) }
        }
    }
}

@Composable
private fun ApplicationsTab(
    data: com.wangzan101.mika.core.career.CareerData,
    service: CareerService,
    selectedApplicationId: String,
    onSelect: (String) -> Unit,
    onPrepare: (ApplicationRecord) -> Unit,
    onApproval: (ApplicationRecord) -> Unit,
    onRecord: (ApplicationRecord) -> Unit,
    onVerify: (ApplicationRecord) -> Unit,
    onTransition: (ApplicationRecord, ApplicationStatus) -> Unit,
) {
    val selected = data.applications.firstOrNull { it.id == selectedApplicationId }
    if (selected != null) {
        ApplicationDetail(
            app = selected, service = service, profile = data.profile,
            onClose = { onSelect("") }, onPrepare = onPrepare, onApproval = onApproval,
            onRecord = onRecord, onVerify = onVerify, onTransition = onTransition,
        )
    }
    GroupLabel("Applications")
    if (data.applications.isEmpty()) {
        EmptyNote("No applications. Open a saved job and choose Prepare application to start with a tailored resume and letter.")
    } else {
        GroupCard {
            data.applications.sortedByDescending { it.updatedAtMs }.forEachIndexed { index, app ->
                ListRow(
                    MikaIcons.Message, app.role.ifBlank { "Application" },
                    subtitle = app.company.ifBlank { "Company not recorded" },
                    first = index == 0,
                    detail = app.events.lastOrNull()?.note?.takeIf { it.isNotBlank() },
                    onClick = { onSelect(app.id) },
                    trailing = { StatusPill(app.status) },
                )
            }
        }
    }
    FooterNote(CareerCopy.USER_SUBMITS)
}

@Composable
private fun ApplicationDetail(
    app: ApplicationRecord,
    service: CareerService,
    profile: CandidateProfile,
    onClose: () -> Unit,
    onPrepare: (ApplicationRecord) -> Unit,
    onApproval: (ApplicationRecord) -> Unit,
    onRecord: (ApplicationRecord) -> Unit,
    onVerify: (ApplicationRecord) -> Unit,
    onTransition: (ApplicationRecord, ApplicationStatus) -> Unit,
) {
    val c = MikaTheme.colors
    val readiness = remember(app, profile) { runCatching { service.review(app.id) }.getOrNull() }
    MikaCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(app.role.ifBlank { "Application" }, style = MikaType.BodyStrong, color = c.ink)
                    Text(app.company.ifBlank { "Company not recorded" }, style = MikaType.Caption, color = c.ink3)
                }
                StatusPill(app.status)
                TextButton(onClick = onClose) { Text("Close", color = c.ink2) }
            }
            Text("Created ${date(app.createdAtMs)} · Updated ${date(app.updatedAtMs)}", Modifier.padding(top = 6.dp), style = MikaType.Hint, color = c.ink3)
            if (app.confirmationReference.isNotBlank()) {
                Text("Reference ${app.confirmationReference}", Modifier.padding(top = 6.dp), style = MikaType.Hint, color = c.ok)
            }
            app.followUpAtMs?.let { followUp ->
                Text("Follow up ${date(followUp)}", Modifier.padding(top = 4.dp), style = MikaType.Hint, color = c.ink2)
            }
            if (readiness != null) {
                if (readiness.blockers.isEmpty()) {
                    Text("Ready for a final human review.", Modifier.padding(top = 8.dp), style = MikaType.Caption, color = c.ok)
                } else {
                    Text("Before submitting:\n" + readiness.blockers.take(4).joinToString("\n") { "• $it" }, Modifier.padding(top = 8.dp), style = MikaType.Hint, color = c.warn)
                }
            }
            when (app.status) {
                ApplicationStatus.Discovered, ApplicationStatus.Shortlisted, ApplicationStatus.Preparing -> {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MikaButton("Prepare docs", { onPrepare(app) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                        MikaButton("Request approval", { onApproval(app) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                    }
                }
                ApplicationStatus.AwaitingApproval -> {
                    MikaButton("I submitted it — record confirmation", { onRecord(app) }, Modifier.fillMaxWidth().padding(top = 12.dp), ButtonKind.Primary, ButtonSize.Medium)
                    Text(CareerCopy.USER_SUBMITS, Modifier.padding(top = 6.dp), style = MikaType.Hint, color = c.ink3)
                }
                ApplicationStatus.Submitted, ApplicationStatus.UnderReview -> {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MikaButton("Verify receipt", { onVerify(app) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                    }
                }
                ApplicationStatus.Interview -> {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MikaButton("Offer", { onTransition(app, ApplicationStatus.Offer) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                        MikaButton("Rejected", { onTransition(app, ApplicationStatus.Rejected) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                    }
                }
                else -> Unit
            }
            if (app.status !in setOf(ApplicationStatus.Offer, ApplicationStatus.Rejected, ApplicationStatus.Withdrawn, ApplicationStatus.Closed)) {
                TextButton(onClick = { onTransition(app, ApplicationStatus.Withdrawn) }, Modifier.fillMaxWidth()) { Text("Withdraw application", color = c.ink3) }
            }
            if (app.events.isNotEmpty()) {
                Text("History", Modifier.padding(top = 10.dp), style = MikaType.Label, color = c.ink3)
                app.events.takeLast(4).reversed().forEach { event ->
                    Text("${date(event.atMs)} · ${event.to.label()}", style = MikaType.Hint, color = c.ink3, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DocumentsTab(
    documents: List<CareerDocument>,
    onPreview: (CareerDocument) -> Unit,
    onExport: (CareerDocument, DocumentFormat) -> Unit,
) {
    val c = MikaTheme.colors
    GroupLabel("Documents")
    if (documents.isEmpty()) {
        EmptyNote("No documents yet. Tailor a resume from a saved job or generate a general resume from the Profile tab's career tools.")
    } else {
        documents.sortedByDescending { it.updatedAtMs }.forEach { doc ->
            MikaCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(34.dp).background(c.accentSoft, MikaShapes.Tile), contentAlignment = Alignment.Center) {
                            Icon(MikaIcons.Doc, null, Modifier.size(19.dp), tint = c.accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(doc.title.ifBlank { doc.kind.name }, style = MikaType.BodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${doc.kind.label()} · v${doc.version} · ${date(doc.createdAtMs)}", style = MikaType.Hint, color = c.ink3)
                        }
                        TextButton(onClick = { onPreview(doc) }) { Text("Preview", color = c.accent) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MikaButton("PDF", { onExport(doc, DocumentFormat.Pdf) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                        MikaButton("DOCX", { onExport(doc, DocumentFormat.Docx) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                        MikaButton("TXT", { onExport(doc, DocumentFormat.Txt) }, Modifier.weight(1f), ButtonKind.Tonal, ButtonSize.Small)
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerStat(label: String, value: Int, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    Column(
        modifier.cardSurface(c).padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MikaType.Title, color = c.ink, maxLines = 1)
        Text(label, style = MikaType.Hint, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProgressBar(percent: Int, color: Color) {
    val c = MikaTheme.colors
    Box(Modifier.fillMaxWidth().height(7.dp).background(c.fill, CircleShape)) {
        Box(Modifier.fillMaxWidth((percent.coerceIn(0, 100) / 100f)).height(7.dp).background(color, CircleShape))
    }
}

@Composable
private fun MatchPill(score: Int) {
    val c = MikaTheme.colors
    val color = when {
        score >= 75 -> c.ok
        score >= 50 -> c.accent
        else -> c.warn
    }
    Box(Modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text("$score%", style = MikaType.Hint, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun StatusPill(status: ApplicationStatus) {
    val c = MikaTheme.colors
    val color = when (status) {
        ApplicationStatus.Offer -> c.ok
        ApplicationStatus.Rejected -> c.bad
        ApplicationStatus.AwaitingApproval -> c.warn
        ApplicationStatus.Interview -> c.accent
        ApplicationStatus.Submitted, ApplicationStatus.UnderReview -> c.accent2
        ApplicationStatus.Withdrawn, ApplicationStatus.Closed -> c.ink3
        else -> c.ink2
    }
    Box(Modifier.background(color.copy(alpha = 0.13f), CircleShape).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(status.label(), style = MikaType.Hint, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CareerChip(text: String) {
    val c = MikaTheme.colors
    Box(Modifier.background(c.fill, CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)) {
        Text(text, style = MikaType.Hint, color = c.ink2, maxLines = 1)
    }
}

internal fun ApplicationStatus.label(): String = when (this) {
    ApplicationStatus.Discovered -> "Discovered"
    ApplicationStatus.Shortlisted -> "Shortlisted"
    ApplicationStatus.Preparing -> "Preparing"
    ApplicationStatus.AwaitingApproval -> "Awaiting approval"
    ApplicationStatus.Submitted -> "Submitted"
    ApplicationStatus.UnderReview -> "Under review"
    ApplicationStatus.Interview -> "Interview"
    ApplicationStatus.Offer -> "Offer"
    ApplicationStatus.Rejected -> "Rejected"
    ApplicationStatus.Withdrawn -> "Withdrawn"
    ApplicationStatus.Closed -> "Closed"
}

internal fun CareerDocumentKind.label(): String = when (this) {
    CareerDocumentKind.Resume -> "Resume"
    CareerDocumentKind.CoverLetter -> "Cover letter"
}

internal fun date(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy"))

internal fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "M" }

private fun mimeOf(format: DocumentFormat): String = when (format) {
    DocumentFormat.Pdf -> "application/pdf"
    DocumentFormat.Docx -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    DocumentFormat.Txt -> "text/plain"
}

/** Copy an exported document to Downloads/Jobby through MediaStore; no broad storage permission is needed. */
private fun publishToDownloads(context: Context, file: File, mime: String): String {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Jobby")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Could not create a Downloads entry.")
    try {
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { input -> input.copyTo(out) } }
            ?: throw IllegalStateException("Could not open the Downloads entry.")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
    return file.name
}
