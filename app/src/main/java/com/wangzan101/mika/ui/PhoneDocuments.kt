package com.wangzan101.mika.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.wangzan101.mika.core.career.DocumentText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 从手机存储里读到的一份文件:名字、抽出来的纯文本,和抽不出全部内容时的说明。 */
data class PickedText(val fileName: String, val text: String, val warnings: List<String> = emptyList())

/**
 * 用户从手机里挑的一份文件变成文字。PDF、DOCX、TXT/Markdown 都用核心的
 * [DocumentText] 抽,不额外引依赖;读之前先看大小,太大的直接拒绝,免得把手机读卡住。
 */
object PhoneDocuments {
    /** 一次最多读这么多;正常的 CV、SKILL.md 远小于此。 */
    const val MAX_BYTES = 8 * 1024 * 1024

    /** 文件选择器接受的类型:纯文本、Markdown、PDF、DOCX,以及不报类型的文件(按内容抽)。 */
    val MIME_TYPES = arrayOf(
        "text/*",
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/octet-stream",
    )

    suspend fun read(context: Context, uri: Uri): Result<PickedText> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported.txt"
            val declared = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
            require(declared == null || declared <= MAX_BYTES) { "That file is too large to import. Pick one under 8 MB." }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BYTES) { "That file is too large to import. Pick one under 8 MB." }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            } ?: error("This file could not be opened. Try another one.")
            val extracted = DocumentText.extract(name, bytes)
            require(extracted.text.isNotBlank()) { "No readable text was found in that file. If it is a scan, paste the text instead." }
            PickedText(name, extracted.text, extracted.warnings)
        }
    }
}

/**
 * 一个「从手机里选文件」的动作:点了打开系统文件选择器,读到的文字交给 [onPicked],读不了的原因交给 [onError]。
 * 选择器开着时 App 可能被回收,所以只在回调里读一次,不缓存 Uri。
 */
@Composable
fun rememberTextFilePicker(
    onPicked: (PickedText) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            PhoneDocuments.read(context, uri).fold(
                onSuccess = onPicked,
                onFailure = { onError(it.message ?: "This file could not be read.") },
            )
        }
    }
    return {
        runCatching { launcher.launch(PhoneDocuments.MIME_TYPES) }
            .onFailure { onError("No file picker is available on this device.") }
    }
}
