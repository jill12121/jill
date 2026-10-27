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
        val created = CleanupLedger<Uri>()
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
            val ownedRoot = openOrCreateOwnedRoot(organizerRoot)
            adoption = ownedRoot.adoption
            val probeRoot = ownedRoot.uri
            val albums = ensureDirectory(probeRoot, "Albums")
            ensureDirectory(probeRoot, "Review")
            val trash = ensureDirectory(probeRoot, "Trash")
            ensureDirectory(trash, "files")
            ensureDirectory(trash, "meta")
            dirsReady = true

            val binary = fileFlow(probeRoot, token, "application/octet-stream", "bin", created)
            val webp = fileFlow(probeRoot, token, "image/webp", "webp", created)
            partReadback = binary.partReadback && webp.partReadback
            renamed = binary.renamed && webp.renamed
            finalReadback = binary.finalReadback && webp.finalReadback
            renamedName = webp.actualName
            renamedMime = webp.actualMime
            oldReadable = webp.oldUriReadable
            fileFlags = flags(webp.finalUri)

            collideCreate = createCollision(probeRoot, webp.finalUri, webp.actualName, created)
            collideRename = renameCollision(probeRoot, token, created)
            val repeatedAlbums = requireNotNull(create(probeRoot, DIR_MIME, "Albums"))
            if (repeatedAlbums != albums) created.register(repeatedAlbums)
            dirIdempotent = classifyCreated(albums, repeatedAlbums, "Albums")
            performance = performanceProbe(probeRoot, token, created)
        } catch (caught: Exception) {
            failure = caught
        } finally {
            cleanup = created.cleanup {
                val deleted = runCatching { DocumentsContract.deleteDocument(resolver, it) }
                    .getOrDefault(false)
                deleted || runCatching { metadata(it); false }.getOrDefault(true)
            }
        }

        S01ProbeResult(
            Build.MODEL, Build.VERSION.SDK_INT, Build.MANUFACTURER,
            Build.VERSION.SECURITY_PATCH,
            S01ProbeResult.redactFingerprint(Build.FINGERPRINT),
            classifyRoot(root),
            SafProbeRules.downloadRootSelection(DocumentsContract.getTreeDocumentId(root)),
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
        val ownedRoot = findOwnedRoot(organizerRoot) ?: return@withContext false
        val created = CleanupLedger<Uri>()
        val operationSucceeded = runCatching {
            val bytes = "s01-retry".encodeToByteArray()
            val uri = created.register(requireNotNull(
                create(ownedRoot.uri, "application/octet-stream", ".s01-retry.part"),
            ))
            write(uri, bytes)
            check(read(uri).contentEquals(bytes))
        }.isSuccess
        val cleanupSucceeded = created.cleanup {
            runCatching { DocumentsContract.deleteDocument(resolver, it) }.getOrDefault(false)
        }
        operationSucceeded && cleanupSucceeded
    }

    private fun fileFlow(
        parent: Uri,
        token: String,
        mime: String,
        extension: String,
        created: CleanupLedger<Uri>,
    ): FileFlow {
        val partName = ".s01-$token.$extension.part"
        val finalName = ".s01-$token.$extension"
        val bytes = "s01-$mime-$token".encodeToByteArray()
        val part = created.register(requireNotNull(create(parent, mime, partName)))
        check(metadata(part).name == partName)
        write(part, bytes)
        val partReadback = read(part).contentEquals(bytes)
        val final = created.register(
            requireNotNull(DocumentsContract.renameDocument(resolver, part, finalName)),
        )
        val actual = metadata(final)
        check(actual.name == finalName && actual.mime == mime)
        val oldReadable = runCatching { read(part); true }.getOrDefault(false)
        return FileFlow(
            partReadback, true, read(final).contentEquals(bytes), actual.name, actual.mime,
            oldReadable, final,
        )
    }

    private fun openOrCreateOwnedRoot(organizerRoot: Uri): OwnedRoot {
        val existing = findOwnedRoot(organizerRoot)
        if (existing != null) return existing.copy(adoption = adoptionProbe(existing))
        val unownedCandidateSeen = children(organizerRoot).any {
            it.mime == DIR_MIME && it.name.startsWith(ProbeOwnership.DIRECTORY_PREFIX)
        }
        val ownership = ProbeOwnership.create(
            UUID.randomUUID().toString().replace("-", ""),
        )
        val root = requireNotNull(create(organizerRoot, DIR_MIME, ownership.directoryName))
        check(metadata(root).name == ownership.directoryName)
        check(childCount(root) == 0)
        requireNotNull(create(root, "application/octet-stream", ProbeOwnership.MARKER_NAME)).also {
            check(metadata(it).name == ProbeOwnership.MARKER_NAME)
            write(it, ownership.markerBytes)
        }
        requireNotNull(create(root, "application/octet-stream", ownership.sampleName)).also {
            check(metadata(it).name == ownership.sampleName)
            write(it, ownership.sampleBytes)
        }
        return OwnedRoot(
            root,
            ownership,
            Adoption(classification = if (unownedCandidateSeen) "NOT_ADOPTABLE" else "NOT_TESTED"),
        )
    }

    private fun adoptionProbe(ownedRoot: OwnedRoot): Adoption {
        val sample = requireNotNull(findChild(ownedRoot.uri, ownedRoot.ownership.sampleName))
        val orphanRead = runCatching {
            read(sample).contentEquals(ownedRoot.ownership.sampleBytes)
        }.getOrDefault(false)
        val orphanWrite = orphanRead && runCatching {
            write(sample, ownedRoot.ownership.sampleBytes)
            read(sample).contentEquals(ownedRoot.ownership.sampleBytes)
        }.getOrDefault(false)
        val created = CleanupLedger<Uri>()
        val deleteOperationSucceeded = orphanWrite && runCatching {
            findChild(ownedRoot.uri, ownedRoot.ownership.adoptionDeleteName)?.let {
                check(DocumentsContract.deleteDocument(resolver, it))
            }
            val candidate = created.register(requireNotNull(
                create(
                    ownedRoot.uri,
                    "application/octet-stream",
                    ownedRoot.ownership.adoptionDeleteName,
                ),
            ))
            write(candidate, ownedRoot.ownership.sampleBytes)
            check(read(candidate).contentEquals(ownedRoot.ownership.sampleBytes))
            check(DocumentsContract.deleteDocument(resolver, candidate))
        }.isSuccess
        val cleanupSucceeded = created.cleanup {
            runCatching {
                DocumentsContract.deleteDocument(resolver, it) ||
                    runCatching { metadata(it); false }.getOrDefault(true)
            }.getOrDefault(false)
        }
        val orphanDelete = deleteOperationSucceeded && cleanupSucceeded
        val classification = when {
            orphanRead && orphanWrite && orphanDelete -> "READ_WRITE_DELETE"
            orphanRead -> "READ_ONLY_ORPHAN"
            else -> "INACCESSIBLE"
        }
        return Adoption(orphanRead, orphanWrite, orphanDelete, classification)
    }

    private fun findOwnedRoot(organizerRoot: Uri): OwnedRoot? {
        return children(organizerRoot)
            .asSequence()
            .filter { it.mime == DIR_MIME && it.name.startsWith(ProbeOwnership.DIRECTORY_PREFIX) }
            .mapNotNull { candidate ->
                val marker = findChild(candidate.uri, ProbeOwnership.MARKER_NAME)
                    ?: return@mapNotNull null
                val markerBytes = runCatching { read(marker) }.getOrNull()
                    ?: return@mapNotNull null
                val markerText = markerBytes.decodeToString()
                val sampleName = markerText.lineSequence()
                    .firstOrNull { it.startsWith("sample=") }
                    ?.substringAfter('=')
                    ?: return@mapNotNull null
                val sample = findChild(candidate.uri, sampleName) ?: return@mapNotNull null
                val sampleBytes = runCatching { read(sample) }.getOrNull()
                    ?: return@mapNotNull null
                val ownership = ProbeOwnership.validate(
                    candidate.name, markerBytes, sampleName, sampleBytes,
                ) ?: return@mapNotNull null
                OwnedRoot(candidate.uri, ownership, Adoption())
            }
            .firstOrNull()
    }

    private fun createCollision(
        parent: Uri,
        existing: Uri,
        name: String,
        created: CleanupLedger<Uri>,
    ): String = runCatching {
        val duplicate = created.register(requireNotNull(create(parent, "image/webp", name)))
        classifyCreated(existing, duplicate, name)
    }.getOrElse { "ERROR_${it.javaClass.simpleName}" }

    private fun renameCollision(
        parent: Uri,
        token: String,
        created: CleanupLedger<Uri>,
    ): String = runCatching {
        val targetName = ".s01-collision-$token.bin"
        val target = created.register(
            requireNotNull(create(parent, "application/octet-stream", targetName)),
        )
        val source = created.register(
            requireNotNull(create(parent, "application/octet-stream", ".s01-source-$token.bin")),
        )
        val renamed = requireNotNull(DocumentsContract.renameDocument(resolver, source, targetName))
            .also(created::register)
        classifyCreated(target, renamed, targetName)
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
        created: CleanupLedger<Uri>,
    ): Performance {
        val perf = created.register(
            requireNotNull(create(root, "application/octet-stream", ".s01-perf-$token.bin")),
        )
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
        val enumDir = created.register(
            requireNotNull(create(root, DIR_MIME, ".s01-enum-$token")),
        )
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
        return children(parent).firstOrNull { it.name == name }?.uri
    }

    private fun children(parent: Uri): List<Child> {
        val parentId = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
        return resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        Child(
                            DocumentsContract.buildDocumentUriUsingTree(parent, it.getString(0)),
                            it.getString(1),
                            it.getString(2),
                        ),
                    )
                }
            }
        }.orEmpty()
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
        return SafProbeRules.classifyTreeDocumentId(DocumentsContract.getTreeDocumentId(root))
    }

    private data class Metadata(val name: String, val mime: String)
    private data class Child(val uri: Uri, val name: String, val mime: String)
    private data class OwnedRoot(
        val uri: Uri,
        val ownership: ProbeOwnership,
        val adoption: Adoption,
    )
    private data class FileFlow(
        val partReadback: Boolean, val renamed: Boolean, val finalReadback: Boolean,
        val actualName: String, val actualMime: String, val oldUriReadable: Boolean,
        val finalUri: Uri,
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
