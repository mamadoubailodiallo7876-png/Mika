package com.wangzan101.mika.core.career

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Optional at-rest encoding for the career database. The app can pass the same key material it
 * already uses for provider keys; the default keeps the file readable inside the app-private
 * directory, which Android already isolates from other apps.
 */
interface CareerCodec {
    fun encode(plain: String): String
    fun decode(stored: String): String

    object Plain : CareerCodec {
        override fun encode(plain: String): String = plain
        override fun decode(stored: String): String = stored
    }
}

/**
 * Device-local career persistence.
 *
 * - One JSON document, written atomically (temp file + rename) so a crash cannot truncate it.
 * - Unknown fields are ignored, so older app versions can still read newer minor additions.
 * - A corrupt file is preserved as `career.corrupt-<timestamp>.json` instead of being deleted.
 * - Every update is serialised through one lock; tools and the UI can share a single instance.
 * - Generated documents are exported to a separate `exports/` directory with sanitised names.
 */
class CareerStore(
    private val dir: File,
    private val codec: CareerCodec = CareerCodec.Plain,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ids: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID().toString().take(10)}" },
) {
    private val dataFile = File(dir, FILE)
    private val exportsDir = File(dir, "exports")
    private val lock = Any()
    private var cache: CareerData? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val pretty = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun newId(prefix: String): String = ids(prefix)

    fun now(): Long = clock()

    /** Current snapshot. Cheap after the first load. */
    fun snapshot(): CareerData = synchronized(lock) { loadLocked() }

    /**
     * Read-modify-write in one lock. The callback must be pure; it may be called again if the
     * on-disk data changed underneath (it cannot while the lock is held, but keeping it pure makes
     * retries safe). Persistence happens before the new value is returned.
     */
    fun update(transform: (CareerData) -> CareerData): CareerData = synchronized(lock) {
        val current = loadLocked()
        val next = transform(current).copy(schemaVersion = CAREER_SCHEMA, updatedAtMs = clock())
        writeLocked(next)
        cache = next
        next
    }

    fun saveProfile(profile: CandidateProfile): CareerData =
        update { it.copy(profile = profile.copy(updatedAtMs = clock())) }

    fun confirmImportedFacts(): CareerData = update { data -> data.copy(profile = data.profile.confirmProposals()) }

    /** Delete the career database and every export. Used by the privacy control only. */
    fun eraseEverything() = synchronized(lock) {
        cache = CareerData(updatedAtMs = clock())
        if (dataFile.exists()) dataFile.delete()
        File(dir, AUDIT).takeIf { it.exists() }?.delete()
        exportsDir.listFiles()?.forEach { it.delete() }
        writeLocked(cache!!)
    }

    /**
     * Export a document. Returns the written file. The caller owns sharing; this module never
     * uploads anything. Existing files are never overwritten: a numeric suffix is added.
     */
    fun writeExport(baseName: String, bytes: ByteArray): File = synchronized(lock) {
        check(bytes.size <= MAX_EXPORT_BYTES) { "document is too large to export" }
        exportsDir.mkdirs()
        val file = uniqueFile(safeFileName(baseName))
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
        file
    }

    fun exports(): List<File> = exportsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    fun profileJson(): String = pretty.encodeToString(snapshot().profile)

    fun readProfileJson(text: String): Result<CandidateProfile> = runCatching {
        val profile = json.decodeFromString<CandidateProfile>(text)
        require(profile.fullName.isNotBlank() || profile.email.isNotBlank() || profile.experience.isNotEmpty()) {
            "not a candidate profile"
        }
        profile
    }

    fun document(id: String): CareerDocument? = snapshot().documents.firstOrNull { it.id == id }

    /**
     * A deliberately small audit trail of career actions. It records action names and ids only:
     * never profile values, document text, confirmation messages or credentials.
     */
    fun audit(action: String, subject: String = "", outcome: String = "ok") = synchronized(lock) {
        runCatching {
            dir.mkdirs()
            val line = "{\"at\":${clock()},\"action\":\"${jsonSafe(action)}\",\"subject\":\"${jsonSafe(subject)}\",\"outcome\":\"${jsonSafe(outcome)}\"}\n"
            val file = File(dir, AUDIT)
            file.appendText(line, Charsets.UTF_8)
            val lines = file.readLines(Charsets.UTF_8)
            if (lines.size > MAX_AUDIT_LINES) {
                val trimmed = lines.takeLast(MAX_AUDIT_LINES).joinToString("\n", postfix = "\n")
                file.writeText(trimmed, Charsets.UTF_8)
            }
        }
    }

    fun auditLog(): List<String> = synchronized(lock) {
        File(dir, AUDIT).takeIf { it.isFile }?.readLines(Charsets.UTF_8).orEmpty()
    }

    private fun jsonSafe(value: String): String = value.take(120).replace(Regex("[^A-Za-z0-9_.:@-]"), "_")

    private fun loadLocked(): CareerData {
        cache?.let { return it }
        val data = if (!dataFile.isFile) {
            CareerData(updatedAtMs = clock())
        } else {
            runCatching { json.decodeFromString<CareerData>(codec.decode(dataFile.readText(Charsets.UTF_8))) }
                .getOrElse {
                    // Never destroy data we failed to understand: keep a copy for recovery.
                    val backup = File(dir, "career.corrupt-${clock()}.json")
                    runCatching { dataFile.copyTo(backup, overwrite = false) }
                    CareerData(updatedAtMs = clock())
                }
        }
        val normalized = data.copy(
            schemaVersion = data.schemaVersion.takeIf { it > 0 } ?: CAREER_SCHEMA,
            profile = data.profile.copy(updatedAtMs = data.profile.updatedAtMs.takeIf { it > 0 } ?: clock()),
        )
        cache = normalized
        return normalized
    }

    private fun writeLocked(data: CareerData) {
        dir.mkdirs()
        val encoded = codec.encode(json.encodeToString(data))
        val tmp = File.createTempFile("career", ".tmp", dir)
        try {
            FileOutputStream(tmp).use { out ->
                out.write(encoded.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            try {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun uniqueFile(name: String): File {
        var candidate = File(exportsDir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var n = 2
        while (candidate.exists()) {
            val suffix = if (ext.isEmpty()) "-$n" else "-$n.$ext"
            candidate = File(exportsDir, "$stem$suffix")
            n++
        }
        return candidate
    }

    private fun safeFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.', '_')
        val base = cleaned.ifEmpty { "career-document" }.take(MAX_NAME)
        return if (base.startsWith(".")) "_$base" else base
    }

    private fun CandidateProfile.confirmProposals(): CandidateProfile = copy(
        education = education.map { if (it.confirmed) it else it.copy(source = FactSource.UserProvided, confirmed = true) },
        qualifications = qualifications.map { if (it.confirmed) it else it.copy(source = FactSource.UserProvided, confirmed = true) },
        experience = experience.map { if (it.confirmed) it else it.copy(source = FactSource.UserProvided, confirmed = true) },
        skills = skills.map { if (it.confirmed) it else it.copy(source = FactSource.UserProvided, confirmed = true) },
        certifications = certifications.map { if (it.confirmed) it else it.copy(source = FactSource.UserProvided, confirmed = true) },
        projects = projects.map { if (it.confirmed) it else it.copy(source = FactSource.UserProvided, confirmed = true) },
        languages = languages.map { if (it.confirmed) it else it.copy(source = FactSource.UserProvided, confirmed = true) },
    )

    companion object {
        const val FILE = "career.json"
        const val AUDIT = "audit.log"
        private const val MAX_NAME = 80
        private const val MAX_EXPORT_BYTES = 8 * 1024 * 1024
        private const val MAX_AUDIT_LINES = 400
    }
}
