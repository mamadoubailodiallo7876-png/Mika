package com.wangzan101.mika.core.cap.career

import com.wangzan101.mika.core.cap.career.CareerToolSupport.choice
import com.wangzan101.mika.core.cap.career.CareerToolSupport.documentJson
import com.wangzan101.mika.core.cap.career.CareerToolSupport.guarded
import com.wangzan101.mika.core.cap.career.CareerToolSupport.matchJson
import com.wangzan101.mika.core.cap.career.CareerToolSupport.service
import com.wangzan101.mika.core.career.CareerDocumentKind
import com.wangzan101.mika.core.career.CareerException
import com.wangzan101.mika.core.career.CoverLetterTone
import com.wangzan101.mika.core.career.DocumentFormat
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
import com.wangzan101.mika.core.tool.ok
import com.wangzan101.mika.core.tool.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object CareerResumeTool : AgentTool {
    override val verb = Verb.CareerWrite
    override val risk = Risk.Read
    override val capability = CAREER_CAPABILITY
    override val offScreen = true
    override val def = ToolDef(
        "career_resume",
        """
        Generate a truthful, ATS-friendly resume from confirmed profile facts. action=tailor (or a jobId) reorders and emphasises the facts that the listing names, but never adds an employer, skill, date or metric. The result includes the document id, version and content, plus the job match and gaps when a job is given. If the profile has no confirmed facts, the tool reports what is missing instead of inventing content.
        """.trimIndent(),
        mapOf(
            "action" to str("What to do.", listOf("generate", "tailor")),
            "jobId" to str("A saved job id. Required for tailor; optional for a general resume."),
            "tailored" to bool("For generate: tailor to jobId when one is given (default true)."),
        ),
        listOf("action"),
    )

    override fun readOnly(args: JsonObject): Boolean = false

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = guarded {
        val action = a.choice("action", listOf("generate", "tailor")) ?: "generate"
        val jobId = a.str("jobId", max = 120).orEmpty()
        if (action == "tailor" && jobId.isBlank()) throw ArgError("jobId", "required for tailor")
        val service = service(ctx)
        val doc = service.generateResume(jobId, tailored = action == "tailor" || a.bool("tailored", true))
        val job = if (jobId.isNotBlank()) service.data().jobs.firstOrNull { it.id == jobId } else null
        ok {
            put("document", documentJson(doc, includeContent = true))
            if (job != null) {
                val match = service.match(job.id)
                put("match", matchJson(match))
                put("gaps", JsonArray(match.missingSkills.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
            put("truthfulness", "Only confirmed profile facts were used; check every line before sending.")
        }
    }
}

internal object CareerCoverLetterTool : AgentTool {
    private val TONES = listOf("Professional", "Concise", "Enthusiastic")

    override val verb = Verb.CareerWrite
    override val risk = Risk.Read
    override val capability = CAREER_CAPABILITY
    override val offScreen = true
    override val def = ToolDef(
        "career_cover_letter",
        """
        Create or save a cover letter. action=generate builds a factual draft from the confirmed profile and a saved job; action=save stores text you wrote after a truthfulness check (contact details must match the profile, claimed years cannot exceed confirmed employment, credentials and skills must exist and be confirmed). It never sends anything. Include the document id in the application with job_application action=prepare.
        """.trimIndent(),
        mapOf(
            "action" to str("generate or save.", listOf("generate", "save")),
            "jobId" to str("Saved job id. Required for generate."),
            "tone" to str("Tone for generate.", TONES),
            "title" to str("For save: document title."),
            "content" to str("For save: the full letter text, at most 200000 characters."),
        ),
        listOf("action"),
    )

    override fun readOnly(args: JsonObject): Boolean = false

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = guarded {
        val action = a.choice("action", listOf("generate", "save")) ?: "generate"
        val service = service(ctx)
        val jobId = a.str("jobId", max = 120).orEmpty()
        if (action == "generate") {
            if (jobId.isBlank()) throw ArgError("jobId", "required for generate")
            val tone = CoverLetterTone.entries.firstOrNull { it.name.equals(a.str("tone") ?: "Professional", true) }
                ?: CoverLetterTone.Professional
            val doc = service.generateCoverLetter(jobId, tone)
            ok {
                put("document", documentJson(doc, includeContent = true))
                put("truthfulness", "Built only from confirmed facts; personalise the opening if you know the team.")
            }
        } else {
            val content = a.str("content", min = 20, max = 200_000) ?: throw ArgError("content", "required")
            val saved = service.saveDraft(
                CareerDocumentKind.CoverLetter,
                a.str("title", max = 200) ?: "Cover letter",
                content, jobId,
            )
            ok {
                put("document", documentJson(saved.document, includeContent = true))
                put("warnings", JsonArray(saved.warnings.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }
        }
    }
}

internal object CareerDocumentTool : AgentTool {
    private val FORMATS = DocumentFormat.entries.map { it.name }

    override val verb = Verb.CareerWrite
    override val risk = Risk.Read
    override val capability = CAREER_CAPABILITY
    override val offScreen = true
    override val def = ToolDef(
        "career_document",
        """
        List career documents or export one as PDF, DOCX or TXT. Exports are written to the app's private career folder and the user can share them from the Career screen; nothing is uploaded. action=get returns one document's content for review. Documents are versioned: generating again never overwrites an earlier version.
        """.trimIndent(),
        mapOf(
            "action" to str("list, get or export.", listOf("list", "get", "export")),
            "documentId" to str("Document id from list or a generation result."),
            "format" to str("Export format.", FORMATS),
        ),
        listOf("action"),
    )

    override fun readOnly(args: JsonObject): Boolean = false

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = guarded {
        val action = a.choice("action", listOf("list", "get", "export")) ?: "list"
        val service = service(ctx)
        when (action) {
            "list" -> ok {
                val docs = service.documents()
                put("documents", JsonArray(docs.map { documentJson(it) }))
                put("count", docs.size)
            }
            "get" -> {
                val id = a.str("documentId", max = 120) ?: throw ArgError("documentId", "required")
                val doc = service.document(id) ?: return@guarded fail("not_found", "No document has id $id.")
                ok { put("document", documentJson(doc, includeContent = true)) }
            }
            "export" -> {
                val id = a.str("documentId", max = 120) ?: throw ArgError("documentId", "required")
                val format = DocumentFormat.entries.firstOrNull { it.name.equals(a.str("format") ?: "Pdf", true) }
                    ?: throw ArgError("format", "must be one of ${FORMATS.joinToString()}")
                val file = service.export(id, format)
                ok {
                    put("fileName", file.name)
                    put("bytes", file.length())
                    put("format", format.name)
                    put("where", "app-private career exports; share from Career > Documents")
                }
            }
            else -> fail("unknown_action", "Unknown action.")
        }
    }
}
