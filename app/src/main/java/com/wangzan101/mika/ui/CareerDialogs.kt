package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.core.career.ApplicationRecord
import com.wangzan101.mika.core.career.CandidateProfile
import com.wangzan101.mika.core.career.CareerDocument
import com.wangzan101.mika.core.career.CareerService
import com.wangzan101.mika.core.career.CareerStore
import com.wangzan101.mika.core.career.EducationEntry
import com.wangzan101.mika.core.career.EmploymentType
import com.wangzan101.mika.core.career.ExperienceEntry
import com.wangzan101.mika.core.career.FactSource
import com.wangzan101.mika.core.career.JobExtractor
import com.wangzan101.mika.core.career.JobIntakeResult
import com.wangzan101.mika.core.career.ProfileLinks
import com.wangzan101.mika.core.career.RemotePreference
import com.wangzan101.mika.core.career.SkillEntry
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/** A compact, theme-aware text field used by the career dialogs. Single line by default. */
@Composable
internal fun CareerField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    val c = MikaTheme.colors
    Column(modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(label, style = MikaType.Label, color = c.ink3, modifier = Modifier.padding(bottom = 5.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth()
                .background(c.fill, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = MikaType.Body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            singleLine = singleLine,
            minLines = minLines,
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(placeholder, style = MikaType.Body, color = c.ink3, maxLines = if (singleLine) 1 else minLines)
                }
                inner()
            },
        )
    }
}

@Composable
private fun DialogBody(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
    ) { content() }
}

@Composable
internal fun ProfileEditDialog(
    profile: CandidateProfile,
    onDismiss: () -> Unit,
    onSave: (CandidateProfile) -> Unit,
) {
    val c = MikaTheme.colors
    var fullName by remember(profile) { mutableStateOf(profile.fullName) }
    var headline by remember(profile) { mutableStateOf(profile.headline) }
    var summary by remember(profile) { mutableStateOf(profile.summary) }
    var email by remember(profile) { mutableStateOf(profile.email) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var location by remember(profile) { mutableStateOf(profile.location) }
    var linkedin by remember(profile) { mutableStateOf(profile.links.linkedin) }
    var github by remember(profile) { mutableStateOf(profile.links.github) }
    var targetRoles by remember(profile) { mutableStateOf(profile.preferences.targetRoles.joinToString(", ")) }
    var remote by remember(profile) { mutableStateOf(profile.preferences.remote) }
    var minimum by remember(profile) { mutableStateOf(profile.preferences.minSalary?.toString().orEmpty()) }

    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit career profile") },
        text = {
            DialogBody {
                Text("Only add facts you can verify. Contact details are used in applications; they are not put in the chat.", style = MikaType.Hint, color = c.ink3)
                CareerField("Full name", fullName, { fullName = it }, "e.g. Alex Morgan")
                CareerField("Professional headline", headline, { headline = it }, "e.g. Android engineer · Kotlin · Compose")
                CareerField("Summary", summary, { summary = it }, "A short summary in your own words", singleLine = false, minLines = 4)
                CareerField("Email", email, { email = it }, "name@example.com")
                CareerField("Phone", phone, { phone = it }, "Optional")
                CareerField("Location", location, { location = it }, "City, country")
                CareerField("LinkedIn", linkedin, { linkedin = it }, "https://linkedin.com/in/…")
                CareerField("GitHub", github, { github = it }, "https://github.com/…")
                CareerField("Target roles", targetRoles, { targetRoles = it }, "Comma-separated, e.g. Android Engineer, Mobile Lead")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remote preference", Modifier.weight(1f), style = MikaType.Label, color = c.ink3)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RemotePreference.entries.forEach { option ->
                            val selected = remote == option
                            TextButton(onClick = { remote = option }) {
                                Text(option.name, color = if (selected) c.accent else c.ink3, style = MikaType.Hint)
                            }
                        }
                    }
                }
                CareerField("Minimum salary", minimum, { minimum = it }, "Optional, annual number")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val roles = targetRoles.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
                onSave(
                    profile.copy(
                        fullName = fullName.trim(),
                        headline = headline.trim(),
                        summary = summary.trim(),
                        email = email.trim(),
                        phone = phone.trim(),
                        location = location.trim(),
                        links = ProfileLinks(
                            github = github.trim(),
                            linkedin = linkedin.trim(),
                            portfolio = profile.links.portfolio,
                            website = profile.links.website,
                        ),
                        preferences = profile.preferences.copy(
                            targetRoles = roles,
                            remote = remote,
                            minSalary = minimum.trim().toIntOrNull()?.takeIf { it >= 0 },
                        ),
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ImportCvDialog(onDismiss: () -> Unit, onImport: (text: String, fileName: String) -> Boolean) {
    val c = MikaTheme.colors
    var text by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("pasted-cv.txt") }
    var fileProblem by remember { mutableStateOf<String?>(null) }
    // 选好的文件直接读成文字并提取:省掉复制粘贴,读完立刻填进资料(仍是待确认的提案)
    val pickFile = rememberTextFilePicker(
        onPicked = { picked ->
            text = picked.text
            fileName = picked.fileName
            fileProblem = picked.warnings.firstOrNull()
            onImport(picked.text, picked.fileName)
        },
        onError = { fileProblem = it },
    )
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import an existing CV") },
        text = {
            DialogBody {
                Text(
                    "Choose a PDF, DOCX or text file from this phone, or paste the CV text. Jobby proposes entries and fills them into your profile, but keeps every imported fact unconfirmed until you review and confirm it. Scanned PDFs need text extraction first.",
                    style = MikaType.Hint, color = c.ink3,
                )
                TextButton({ pickFile() }, Modifier.testTag("cv-import-file")) {
                    Text("Choose a file from this phone")
                }
                fileProblem?.let { Text(it, style = MikaType.Hint, color = c.bad) }
                CareerField("File name (for context)", fileName, { fileName = it }, "pasted-cv.txt")
                CareerField("CV text", text, { text = it }, "Paste your CV here", singleLine = false, minLines = 10)
            }
        },
        confirmButton = {
            TextButton(enabled = text.trim().length >= 40, onClick = { onImport(text, fileName.ifBlank { "pasted-cv.txt" }) }) {
                Text("Extract proposals")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AddExperienceDialog(
    store: CareerStore,
    onDismiss: () -> Unit,
    onSave: (ExperienceEntry) -> Unit,
) {
    var company by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var current by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add experience") },
        text = {
            DialogBody {
                CareerField("Employer", company, { company = it }, "Company or organisation")
                CareerField("Job title", title, { title = it }, "e.g. Android Engineer")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareerField("Start", start, { start = it }, "2021-03", modifier = Modifier.weight(1f))
                    CareerField("End", end, { end = it }, "2024-06", modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Current role", Modifier.weight(1f), style = MikaType.Label, color = MikaTheme.colors.ink3)
                    MikaSwitch(current, { current = it })
                }
                CareerField("Summary", summary, { summary = it }, "What you were responsible for; no invented metrics", singleLine = false, minLines = 3)
            }
        },
        confirmButton = {
            TextButton(enabled = company.isNotBlank() && title.isNotBlank(), onClick = {
                onSave(
                    ExperienceEntry(
                        id = store.newId("exp"), company = company.trim(), title = title.trim(),
                        start = start.trim(), end = end.trim(), current = current, summary = summary.trim(),
                        source = FactSource.UserProvided, confirmed = true,
                    ),
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AddEducationDialog(
    store: CareerStore,
    onDismiss: () -> Unit,
    onSave: (EducationEntry) -> Unit,
) {
    var institution by remember { mutableStateOf("") }
    var degree by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add education") },
        text = {
            DialogBody {
                CareerField("Institution", institution, { institution = it }, "University or school")
                CareerField("Degree", degree, { degree = it }, "e.g. BSc Computer Science")
                CareerField("Field of study", field, { field = it }, "e.g. Computer Science")
                CareerField("Completion year", end, { end = it }, "e.g. 2022")
            }
        },
        confirmButton = {
            TextButton(enabled = institution.isNotBlank(), onClick = {
                onSave(
                    EducationEntry(
                        id = store.newId("edu"), institution = institution.trim(), degree = degree.trim(),
                        fieldOfStudy = field.trim(), end = end.trim(), source = FactSource.UserProvided, confirmed = true,
                    ),
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AddSkillDialog(
    store: CareerStore,
    onDismiss: () -> Unit,
    onSave: (SkillEntry) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add skill") },
        text = {
            DialogBody {
                CareerField("Skill", name, { name = it }, "e.g. Kotlin")
                CareerField("Level (optional)", level, { level = it }, "e.g. Advanced")
                Text("Only add skills you could discuss in an interview.", style = MikaType.Hint, color = MikaTheme.colors.ink3)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(
                    SkillEntry(
                        id = store.newId("skill"), name = name.trim(), level = level.trim(),
                        source = FactSource.UserProvided, confirmed = true,
                    ),
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AddJobDialog(
    service: CareerService,
    onDismiss: () -> Unit,
    onAdded: (JobIntakeResult) -> Unit,
    onError: (String) -> Unit,
) {
    val c = MikaTheme.colors
    var text by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save a job listing") },
        text = {
            DialogBody {
                Text("Paste the listing text. Website text is treated as data, never as instructions. Unknown fields stay empty.", style = MikaType.Hint, color = c.ink3)
                CareerField("Listing text", text, { text = it }, "Paste the job description", singleLine = false, minLines = 9)
                CareerField("Title (optional)", title, { title = it }, "Helps when the text has no clear title")
                CareerField("Company (optional)", company, { company = it }, "Employer name")
                CareerField("Application URL (optional)", url, { url = it }, "https://…")
            }
        },
        confirmButton = {
            TextButton(enabled = text.trim().length >= 40 || title.isNotBlank(), onClick = {
                runCatching {
                    val extracted = if (text.trim().length >= 40) {
                        JobExtractor.fromText(
                            text = text, id = service.store.newId("job"), nowMs = service.now(),
                            url = url.trim(), source = "pasted", titleHint = title.trim(), companyHint = company.trim(),
                        )
                    } else {
                        JobExtractor.fromFields(
                            id = service.store.newId("job"), nowMs = service.now(), title = title.trim(),
                            company = company.trim(), description = "", location = "", salaryText = "",
                            employmentType = EmploymentType.Unspecified, requirements = emptyList(), skills = emptyList(),
                            experienceYears = null, deadline = "", source = "pasted", url = url.trim(),
                            remote = RemotePreference.Unspecified,
                        )
                    }
                    onAdded(service.addJob(extracted))
                }.onFailure { onError(it.message ?: "Could not read this listing.") }
            }) { Text("Save listing") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SubmissionDialog(
    application: ApplicationRecord,
    onDismiss: () -> Unit,
    onRecord: (reference: String, evidence: String, url: String) -> Unit,
) {
    val c = MikaTheme.colors
    var reference by remember(application) { mutableStateOf("") }
    var evidence by remember(application) { mutableStateOf("") }
    var url by remember(application) { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record your submission") },
        text = {
            DialogBody {
                Text(
                    "Only use this after you submitted the application yourself and the employer showed a confirmation page or sent an email. A click alone is not evidence.",
                    style = MikaType.Caption, color = c.warn,
                )
                CareerField("Confirmation reference", reference, { reference = it }, "Reference number or code")
                CareerField("Confirmation text", evidence, { evidence = it }, "Paste the confirmation message shown by the employer", singleLine = false, minLines = 4)
                CareerField("Application URL", url, { url = it }, "Optional")
            }
        },
        confirmButton = {
            TextButton(
                enabled = reference.trim().isNotBlank() || evidence.trim().isNotBlank(),
                onClick = { onRecord(reference.trim(), evidence.trim(), url.trim()) },
            ) { Text("Record submission") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun VerifyDialog(
    application: ApplicationRecord,
    onDismiss: () -> Unit,
    onVerify: (reference: String, source: String, evidence: String) -> Unit,
) {
    var reference by remember(application) { mutableStateOf(application.confirmationReference) }
    var source by remember(application) { mutableStateOf("Confirmation page") }
    var evidence by remember(application) { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify the receipt") },
        text = {
            DialogBody {
                Text("Verification needs text that actually contains the reference. This is not a new submission.", style = MikaType.Hint, color = MikaTheme.colors.ink3)
                CareerField("Reference", reference, { reference = it }, "Reference number")
                CareerField("Source", source, { source = it }, "Email, confirmation page, portal")
                CareerField("Evidence text", evidence, { evidence = it }, "Paste the message that contains the reference", singleLine = false, minLines = 4)
            }
        },
        confirmButton = {
            TextButton(enabled = reference.isNotBlank() && evidence.trim().length >= 5, onClick = {
                onVerify(reference.trim(), source.trim().ifBlank { "Confirmation page" }, evidence.trim())
            }) { Text("Verify") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DocumentPreviewDialog(document: CareerDocument, onDismiss: () -> Unit) {
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.title.ifBlank { "Document" }) },
        text = {
            SelectionContainer {
                Text(
                    document.content,
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    style = MikaType.Code, color = MikaTheme.colors.ink,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
