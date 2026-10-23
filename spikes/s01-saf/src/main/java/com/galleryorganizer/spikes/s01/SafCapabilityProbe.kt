package com.galleryorganizer.spikes.s01

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

class SafCapabilityProbe(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun run(root: Uri): S01ProbeResult = withContext(Dispatchers.IO) {
        val organizerRoot = DocumentsContract.buildDocumentUriUsingTree(
            root, DocumentsContract.getTreeDocumentId(root),
        )
        val created = mutableListOf<Uri>()
        var failure: Exception? = null
        var dirsReady = false
        var partReadback = false
        var renamed = false
        var finalReadback = false
        var cleanup = true
        var renamedName = ""
        var renamedMime = ""
        var collideCreate = "NOT_RUN"
        var collideRename = "NOT_RUN"
        var dirIdempotent = "NOT_RUN"
        var oldReadable = false
        var adoption = Adoption()
        var performance = Performance()
        var rootFlags = emptyList<String>()
        var fileFlags = emptyList<String>()
        val token = UUID.randomUUID().toString().take(8)

        try {
            resolver.takePersistableUriPermission(root, READ_WRITE_FLAGS)
            rootFlags = flags(organizerRoot)
            val albums = ensureDirectory(organizerRoot, "Albums")
            ensureDirectory(organizerRoot, "Review")
            val trash = ensureDirectory(organizerRoot, "Trash")
            ensureDirectory(trash, "files")
            ensureDirectory(trash, "meta")
            dirsReady = true

            adoption = adoptionProbe(organizerRoot, created)
            val binary = fileFlow(organizerRoot, token, "application/octet-stream", "bin")
            val webp = fileFlow(organizerRoot, token, "image/webp", "webp")
            created += binary.cleanupUris + webp.cleanupUris
            partReadback = binary.partReadback && webp.partReadback
            renamed = binary.renamed && webp.renamed
            finalReadback = binary.finalReadback && webp.finalReadback
            renamedName = webp.actualName
            renamedMime = webp.actualMime
            oldReadable = webp.oldUriReadable
            fileFlags = flags(webp.finalUri)

            collideCreate = createCollision(organizerRoot, webp.finalUri, webp.actualName, created)
            collideRename = renameCollision(organizerRoot, token, created)
            val repeatedAlbums = requireNotNull(create(organizerRoot, DIR_MIME, "Albums"))
            if (repeatedAlbums != albums) created += repeatedAlbums
            dirIdempotent = classifyCreated(albums, repeatedAlbums, "Albums")
            performance = performanceProbe(organizerRoot, token, created)
        } catch (caught: Exception) {
            failure = caught
        } finally {
            created.asReversed().distinct().forEach {
                val deleted = runCatching { DocumentsContract.deleteDocument(resolver, it) }
                    .getOrDefault(false)
                cleanup = (deleted || runCatching { metadata(it); false }.getOrDefault(true)) && cleanup
            }
        }

        S01ProbeResult(
            Build.MODEL, Build.VERSION.SDK_INT, Build.FINGERPRINT, classifyRoot(root),
            if (classifyRoot(root) == RootKind.DOWNLOAD) DownloadRootSelection.TRUE
            else DownloadRootSelection.PROMPT_MESSAGE,
            dirsReady, partReadback, renamed, renamedName, renamedMime, finalReadback,
            cleanup, collideCreate, collideRename, dirIdempotent, oldReadable,
            reinstallAdoptionRead = adoption.read,
            reinstallAdoptionWrite = adoption.write,
            reinstallAdoptionDelete = adoption.delete,
            reinstallOrphanClass = adoption.classification,
            writeMbps = performance.writeMbps, readMbps = performance.readMbps,
            peakHeapMb = performance.peakHeapMb, seekSupported = performance.seek,
            enumerate2kMillis = performance.enumerate2kMillis,
            createPerFileMicros = performance.createPerFileMicros,
            enumerate5kMillis = performance.enumerate5kMillis,
            flagsRoot = rootFlags, flagsFile = fileFlags,
            exceptionClass = failure?.javaClass?.simpleName,
            exceptionMaskedMessage = failure?.let { "Probe operation failed; no URI retained" },
        )
    }

    suspend fun retrySavedGrant(root: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!hasPersistedGrant(root)) return@withContext false
        val organizerRoot = DocumentsContract.buildDocumentUriUsingTree(
            root, DocumentsContract.getTreeDocumentId(root),
        )
        val uri = runCatching {
            val bytes = "s01-retry".encodeToByteArray()
            requireNotNull(create(organizerRoot, "application/octet-stream", ".s01-retry.part")).also {
                write(it, bytes)
                check(read(it).contentEquals(bytes))
            }
        }.getOrNull() ?: return@withContext false
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)
    }

    private fun fileFlow(parent: Uri, token: String, mime: String, extension: String): FileFlow {
        val partName = ".s01-$token.$extension.part"
        val finalName = ".s01-$token.$extension"
        val bytes = "s01-$mime-$token".encodeToByteArray()
        val part = requireNotNull(create(parent, mime, partName))
        check(metadata(part).name == partName)
        write(part, bytes)
        val partReadback = read(part).contentEquals(bytes)
        val final = requireNotNull(DocumentsContract.renameDocument(resolver, part, finalName))
        val actual = metadata(final)
        check(actual.name == finalName && actual.mime == mime)
        val oldReadable = runCatching { read(part); true }.getOrDefault(false)
        return FileFlow(
            partReadback, true, read(final).contentEquals(bytes), actual.name, actual.mime,
            oldReadable, final, listOf(final),
        )
    }

    private fun adoptionProbe(root: Uri, created: MutableList<Uri>): Adoption {
        val marker = findChild(root, "root.json")
        val orphan = findChild(root, ".s01-reinstall.bin")
        if (marker == null) {
            create(root, "application/json", "root.json")!!.also {
                write(it, """{"schema":"s01-probe","version":1}""".encodeToByteArray())
            }
            create(root, "application/octet-stream", ".s01-reinstall.bin")!!.also {
                write(it, "legacy".encodeToByteArray())
            }
            return Adoption()
        }
        val markerRead = runCatching { read(marker).isNotEmpty() }.getOrDefault(false)
        val orphanRead = orphan != null && runCatching { read(orphan).isNotEmpty() }.getOrDefault(false)
        val orphanWrite = orphan != null && runCatching {
            write(orphan, "adopted".encodeToByteArray()); true
        }.getOrDefault(false)
        val orphanDelete = orphan != null && runCatching {
            DocumentsContract.deleteDocument(resolver, orphan)
        }.getOrDefault(false)
        val classification = when {
            orphanRead && orphanWrite && orphanDelete -> "READ_WRITE_DELETE"
            orphanRead -> "READ_ONLY_ORPHAN"
            else -> "INACCESSIBLE"
        }
        return Adoption(markerRead && orphanRead, orphanWrite, orphanDelete, classification)
    }

    private fun createCollision(
        parent: Uri,
        existing: Uri,
        name: String,
        created: MutableList<Uri>,
    ): String = runCatching {
        val duplicate = requireNotNull(create(parent, "image/webp", name)).also(created::add)
        classifyCreated(existing, duplicate, name)
    }.getOrElse { "ERROR_${it.javaClass.simpleName}" }

    private fun renameCollision(
        parent: Uri,
        token: String,
        created: MutableList<Uri>,
    ): String = runCatching {
        val targetName = ".s01-collision-$token.bin"
        val target = requireNotNull(create(parent, "application/octet-stream", targetName))
            .also(created::add)
        val source = requireNotNull(create(parent, "application/octet-stream", ".s01-source-$token.bin"))
            .also(created::add)
        val result = requireNotNull(DocumentsContract.renameDocument(resolver, source, targetName))
            .also(created::add)
        classifyCreated(target, result, targetName)
    }.getOrElse { "ERROR_${it.javaClass.simpleName}" }

    private fun classifyCreated(existing: Uri, result: Uri, requested: String): String {
        val actual = metadata(result).name
        return when {
            result == existing -> "RETURNED_EXISTING"
            actual == requested -> "OVERWRITE_OR_DUPLICATE_EXACT"
            else -> "AUTO_RENAME:$actual"
        }
    }

    private fun performanceProbe(
        root: Uri,
        token: String,
        created: MutableList<Uri>,
    ): Performance {
        val perf = requireNotNull(create(root, "application/octet-stream", ".s01-perf-$token.bin"))
            .also(created::add)
        val digest = MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(1024 * 1024)
        var peakHeap = usedHeap()
        val writeStart = SystemClock.elapsedRealtimeNanos()
        resolver.openOutputStream(perf, "wt").use { output ->
            repeat(64) {
                Random.nextBytes(chunk); digest.update(chunk); requireNotNull(output).write(chunk)
                peakHeap = maxOf(peakHeap, usedHeap())
            }
        }
        val expected = digest.digest()
        val writeSeconds = (SystemClock.elapsedRealtimeNanos() - writeStart) / 1e9
        digest.reset()
        val readStart = SystemClock.elapsedRealtimeNanos()
        resolver.openInputStream(perf).use { input ->
            while (true) {
                val count = requireNotNull(input).read(chunk)
                if (count < 0) break
                digest.update(chunk, 0, count)
                peakHeap = maxOf(peakHeap, usedHeap())
            }
        }
        check(digest.digest().contentEquals(expected))
        val readSeconds = (SystemClock.elapsedRealtimeNanos() - readStart) / 1e9
        val seek = runCatching {
            resolver.openFileDescriptor(perf, "r").use { descriptor ->
                FileInputStream(requireNotNull(descriptor).fileDescriptor).channel.use {
                    it.position(32L * 1024 * 1024); it.read(java.nio.ByteBuffer.allocate(1)) == 1
                }
            }
        }.getOrDefault(false)
        val enumDir = requireNotNull(create(root, DIR_MIME, ".s01-enum-$token")).also(created::add)
        val createStart = SystemClock.elapsedRealtimeNanos()
        repeat(2000) { create(enumDir, "application/octet-stream", "f$it") }
        val createMicros = (SystemClock.elapsedRealtimeNanos() - createStart) / 2000 / 1000
        val enum2k = timedEnumeration(enumDir, 2000)
        val enum5k = if (enum2k >= 0) {
            repeat(3000) { create(enumDir, "application/octet-stream", "g$it") }
            timedEnumeration(enumDir, 5000)
        } else null
        return Performance(
            64.0 / writeSeconds, 64.0 / readSeconds, peakHeap / 1024.0 / 1024.0,
            seek, enum2k, createMicros, enum5k,
        )
    }

    private fun timedEnumeration(parent: Uri, expected: Int): Long {
        val start = SystemClock.elapsedRealtime()
        check(childCount(parent) == expected)
        return SystemClock.elapsedRealtime() - start
    }

    private fun ensureDirectory(parent: Uri, name: String): Uri =
        findChild(parent, name) ?: requireNotNull(create(parent, DIR_MIME, name))

    private fun create(parent: Uri, mime: String, name: String): Uri? =
        DocumentsContract.createDocument(resolver, parent, mime, name)

    private fun write(uri: Uri, bytes: ByteArray) =
        resolver.openOutputStream(uri, "wt").use { requireNotNull(it).write(bytes) }

    private fun read(uri: Uri): ByteArray =
        resolver.openInputStream(uri).use { requireNotNull(it).readBytes() }

    private fun metadata(uri: Uri): Metadata = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
        null, null, null,
    )!!.use { check(it.moveToFirst()); Metadata(it.getString(0), it.getString(1)) }

    private fun flags(uri: Uri): List<String> {
        val value = resolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null,
        )!!.use { check(it.moveToFirst()); it.getInt(0) }
        return FLAG_NAMES.filter { value and it.first != 0 }.map { it.second }
    }

    private fun findChild(parent: Uri, name: String): Uri? {
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use {
            while (it.moveToNext()) if (it.getString(1) == name) {
                return DocumentsContract.buildDocumentUriUsingTree(parent, it.getString(0))
            }
        }
        return null
    }

    private fun childCount(parent: Uri): Int {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent, DocumentsContract.getDocumentId(parent),
        )
        return resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.count } ?: 0
    }

    private fun hasPersistedGrant(root: Uri) = resolver.persistedUriPermissions.any {
        it.uri == root && it.isReadPermission && it.isWritePermission
    }

    private fun usedHeap(): Long =
        Runtime.getRuntime().run { totalMemory() - freeMemory() }

    private fun classifyRoot(root: Uri): RootKind {
        val id = DocumentsContract.getTreeDocumentId(root).substringAfter(':').trimEnd('/').lowercase()
        return when {
            id == "download" || id.startsWith("download/") -> RootKind.DOWNLOAD
            id == "pictures" || id.startsWith("pictures/") -> RootKind.PICTURES
            else -> RootKind.OTHER
        }
    }

    private data class Metadata(val name: String, val mime: String)
    private data class FileFlow(
        val partReadback: Boolean, val renamed: Boolean, val finalReadback: Boolean,
        val actualName: String, val actualMime: String, val oldUriReadable: Boolean,
        val finalUri: Uri, val cleanupUris: List<Uri>,
    )
    private data class Adoption(
        val read: Boolean = false, val write: Boolean = false, val delete: Boolean = false,
        val classification: String = "NOT_TESTED",
    )
    private data class Performance(
        val writeMbps: Double = 0.0, val readMbps: Double = 0.0,
        val peakHeapMb: Double = 0.0, val seek: Boolean = false,
        val enumerate2kMillis: Long = 0, val createPerFileMicros: Long = 0,
        val enumerate5kMillis: Long? = null,
    )

    private companion object {
        const val DIR_MIME = DocumentsContract.Document.MIME_TYPE_DIR
        const val READ_WRITE_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val FLAG_NAMES = listOf(
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE to "SUPPORTS_WRITE",
            DocumentsContract.Document.FLAG_SUPPORTS_RENAME to "SUPPORTS_RENAME",
            DocumentsContract.Document.FLAG_SUPPORTS_DELETE to "SUPPORTS_DELETE",
            DocumentsContract.Document.FLAG_SUPPORTS_MOVE to "SUPPORTS_MOVE",
            DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE to "DIR_SUPPORTS_CREATE",
        )
    }
}
