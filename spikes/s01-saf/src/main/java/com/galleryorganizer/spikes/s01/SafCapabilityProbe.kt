package com.galleryorganizer.spikes.s01

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SafCapabilityProbe(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun run(treeUri: Uri): S01ProbeResult = withContext(Dispatchers.IO) {
        val rootKind = classifyRoot(treeUri)
        var organizerReady = false
        var partReadback = false
        var renameSucceeded = false
        var finalReadback = false
        var cleanupSucceeded = true
        var probeUri: Uri? = null
        var error: String? = null

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(treeUri, flags)
            val organizerUri = findChild(treeUri, "GalleryOrganizer")
                ?: DocumentsContract.createDocument(
                    resolver,
                    treeUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    "GalleryOrganizer",
                )
            organizerReady = organizerUri != null
            requireNotNull(organizerUri)

            val token = UUID.randomUUID().toString().take(8)
            val bytes = "s01-saf-probe-$token".encodeToByteArray()
            probeUri = DocumentsContract.createDocument(
                resolver,
                organizerUri,
                "application/octet-stream",
                ".s01-$token.bin.part",
            )
            requireNotNull(probeUri)
            resolver.openOutputStream(probeUri, "wt").use { output ->
                requireNotNull(output).write(bytes)
            }
            partReadback = resolver.openInputStream(probeUri).use {
                requireNotNull(it).readBytes().contentEquals(bytes)
            }

            val renamed = DocumentsContract.renameDocument(
                resolver,
                probeUri,
                ".s01-$token.bin",
            )
            renameSucceeded = renamed != null
            if (renamed != null) {
                probeUri = renamed
                finalReadback = resolver.openInputStream(renamed).use {
                    requireNotNull(it).readBytes().contentEquals(bytes)
                }
            }
        } catch (failure: Exception) {
            error = failure::class.simpleName ?: "ProbeFailure"
        } finally {
            probeUri?.let {
                cleanupSucceeded = runCatching {
                    DocumentsContract.deleteDocument(resolver, it)
                }.getOrDefault(false)
            }
        }

        S01ProbeResult(
            rootKind = rootKind,
            permissionPersisted = hasPersistedGrant(treeUri),
            organizerRootReady = organizerReady,
            partWriteReadback = partReadback,
            renameSucceeded = renameSucceeded,
            finalReadback = finalReadback,
            cleanupSucceeded = cleanupSucceeded,
            error = error,
        )
    }

    suspend fun verifySavedGrant(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        hasPersistedGrant(treeUri) &&
            runCatching { findChild(treeUri, "GalleryOrganizer") != null }
                .getOrDefault(false)
    }

    private fun hasPersistedGrant(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }

    private fun classifyRoot(treeUri: Uri): RootKind {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        return when (documentId.substringAfter(':').trimEnd('/').lowercase()) {
            "download" -> RootKind.DOWNLOAD
            "pictures" -> RootKind.PICTURES
            else -> RootKind.OTHER
        }
    }

    private fun findChild(parentUri: Uri, displayName: String): Uri? {
        val parentId = DocumentsContract.getDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            parentId,
        )
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == displayName) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        parentUri,
                        cursor.getString(0),
                    )
                }
            }
        }
        return null
    }
}
