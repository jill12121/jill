package com.galleryorganizer.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.StorageFailure
import com.galleryorganizer.contract.WorkspaceAdoptionSnapshot
import com.galleryorganizer.contract.WorkspaceAlbumEvidence
import com.galleryorganizer.contract.WorkspaceRootGateway
import com.galleryorganizer.contract.WorkspaceRootMode
import com.galleryorganizer.contract.WorkspaceRootSelection
import com.galleryorganizer.domain.DefaultWorkspaceSetup
import com.galleryorganizer.domain.WorkspaceSetup
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun createSafWorkspaceSetup(context: Context): WorkspaceSetup =
    DefaultWorkspaceSetup(SafWorkspaceRootGateway(context.applicationContext))

class SafWorkspaceRootGateway(context: Context) : WorkspaceRootGateway {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun persistSelection(
        uri: String,
        mode: WorkspaceRootMode,
    ): FsResult<WorkspaceRootSelection> = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(uri)
        if (treeUri.scheme != "content" || !DocumentsContract.isTreeUri(treeUri)) {
            return@withContext FsResult.Failure(StorageFailure.NotFound)
        }
        try {
            val previousUri = preferences.getString(KEY_URI, null)
            resolver.takePersistableUriPermission(treeUri, GRANT_FLAGS)
            val selection = WorkspaceRootSelection(
                uri = treeUri.toString(),
                displayName = displayName(treeUri),
                relativePath = relativePath(treeUri),
                mode = mode,
            )
            if (!preferences.edit()
                    .putString(KEY_URI, selection.uri)
                    .putString(KEY_NAME, selection.displayName)
                    .putString(KEY_RELATIVE_PATH, selection.relativePath)
                    .putString(KEY_MODE, selection.mode.name)
                    .commit()
            ) {
                runCatching { resolver.releasePersistableUriPermission(treeUri, GRANT_FLAGS) }
                FsResult.Failure(StorageFailure.ProviderUnavailable())
            } else {
                previousUri?.takeIf { it != selection.uri }?.let {
                    runCatching {
                        resolver.releasePersistableUriPermission(Uri.parse(it), GRANT_FLAGS)
                    }
                }
                FsResult.Success(selection)
            }
        } catch (_: SecurityException) {
            FsResult.Failure(StorageFailure.AccessDenied)
        } catch (_: IllegalArgumentException) {
            FsResult.Failure(StorageFailure.NotFound)
        } catch (_: RuntimeException) {
            FsResult.Failure(StorageFailure.ProviderUnavailable())
        }
    }

    override suspend fun loadSelection(): FsResult<WorkspaceRootSelection?> =
        withContext(Dispatchers.IO) {
            val uri = preferences.getString(KEY_URI, null)
                ?: return@withContext FsResult.Success(null)
            try {
                val mode = WorkspaceRootMode.valueOf(
                    preferences.getString(KEY_MODE, null).orEmpty(),
                )
                val treeUri = Uri.parse(uri)
                val granted = resolver.persistedUriPermissions.any {
                    it.uri == treeUri && it.isReadPermission && it.isWritePermission
                }
                if (!granted) return@withContext FsResult.Failure(StorageFailure.AccessDenied)
                FsResult.Success(
                    WorkspaceRootSelection(
                        uri = uri,
                        displayName = preferences.getString(KEY_NAME, null) ?: "Archive root",
                        relativePath = preferences.getString(KEY_RELATIVE_PATH, null)
                            ?: relativePath(treeUri),
                        mode = mode,
                    ),
                )
            } catch (_: SecurityException) {
                FsResult.Failure(StorageFailure.AccessDenied)
            } catch (_: IllegalArgumentException) {
                FsResult.Failure(StorageFailure.NotFound)
            } catch (_: RuntimeException) {
                FsResult.Failure(StorageFailure.ProviderUnavailable())
            }
        }

    override suspend fun inspectSelection(): FsResult<WorkspaceAdoptionSnapshot> =
        withContext(Dispatchers.IO) {
            val uri = preferences.getString(KEY_URI, null)
                ?: return@withContext FsResult.Failure(StorageFailure.NotFound)
            try {
                FsResult.Success(inspect(Uri.parse(uri)))
            } catch (_: SecurityException) {
                FsResult.Failure(StorageFailure.AccessDenied)
            } catch (_: IllegalArgumentException) {
                FsResult.Failure(StorageFailure.NotFound)
            } catch (_: RuntimeException) {
                FsResult.Failure(StorageFailure.ProviderUnavailable())
            }
        }

    private fun inspect(treeUri: Uri): WorkspaceAdoptionSnapshot {
        val root = children(treeUri, DocumentsContract.getTreeDocumentId(treeUri), ROOT_LIMIT)
        val control = root.nodes.namedDirectory(".gallery-organizer")
        val albums = root.nodes.namedDirectory("Albums")
        var conflict = root.nodes.hasDuplicate(".gallery-organizer") ||
            root.nodes.hasDuplicate("Albums")
        var truncated = root.truncated

        val marker = control?.let { directory ->
            val listing = children(treeUri, directory.id, DIRECTORY_LIMIT)
            conflict = conflict || listing.nodes.hasDuplicate("root.json")
            truncated = truncated || listing.truncated
            listing.nodes.singleNamedFile("root.json")?.let { readJson(treeUri, it.id) }
        }
        truncated = truncated || marker?.truncated == true

        val albumEvidence = mutableListOf<WorkspaceAlbumEvidence>()
        var albumsPresent = false
        if (albums != null) {
            albumsPresent = true
            val listing = children(treeUri, albums.id, ALBUM_LIMIT)
            truncated = truncated || listing.truncated
            conflict = conflict ||
                listing.nodes.any { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR } ||
                listing.nodes.hasDuplicateNames()
            listing.nodes.filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
                .take(ALBUM_LIMIT)
                .forEach { album ->
                    val entries = children(treeUri, album.id, DIRECTORY_LIMIT)
                    conflict = conflict || entries.nodes.hasDuplicate("manifest.json")
                    truncated = truncated || entries.truncated
                    val manifest = entries.nodes.singleNamedFile("manifest.json")
                        ?.let { readJson(treeUri, it.id) }
                    truncated = truncated || manifest?.truncated == true
                    albumEvidence += WorkspaceAlbumEvidence(album.name, manifest?.json)
                }
        }
        return WorkspaceAdoptionSnapshot(
            rootMarkerJson = marker?.json,
            albums = albumEvidence,
            albumsDirectoryPresent = albumsPresent,
            hasWorkspaceConflict = conflict,
            truncated = truncated,
        )
    }

    private fun children(treeUri: Uri, parentId: String, limit: Int): ChildListing {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val nodes = mutableListOf<DocumentNode>()
        var truncated = false
        resolver.query(uri, DOCUMENT_COLUMNS, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (nodes.size >= limit) {
                    truncated = true
                    break
                }
                nodes += DocumentNode(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    mimeType = cursor.getString(2),
                )
            }
        } ?: throw IllegalStateException("Provider returned no document cursor")
        return ChildListing(nodes, truncated)
    }

    private fun readJson(treeUri: Uri, documentId: String): JsonRead {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val input = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Provider returned no input stream")
        val bytes = input.use { stream ->
            val buffer = ByteArray(JSON_LIMIT + 1)
            var size = 0
            while (size < buffer.size) {
                val count = stream.read(buffer, size, buffer.size - size)
                if (count < 0) break
                size += count
            }
            buffer.copyOf(size)
        }
        if (bytes.size > JSON_LIMIT) return JsonRead(null, true)
        val json = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        return JsonRead(json, false)
    }

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Archive root"

    private fun relativePath(uri: Uri): String? = runCatching {
        val id = DocumentsContract.getTreeDocumentId(uri)
        id.substringAfter(':', id).replace('\\', '/').trim('/').takeIf(String::isNotBlank)
    }.getOrNull()

    private companion object {
        const val PREFERENCES = "workspace-root"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_RELATIVE_PATH = "relative-path"
        const val KEY_MODE = "mode"
        const val GRANT_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        const val JSON_LIMIT = 64 * 1024
        const val ALBUM_LIMIT = 256
        const val ROOT_LIMIT = 32
        const val DIRECTORY_LIMIT = 32
        val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}

private data class DocumentNode(val id: String, val name: String, val mimeType: String)
private data class ChildListing(val nodes: List<DocumentNode>, val truncated: Boolean)
private data class JsonRead(val json: String?, val truncated: Boolean)

private fun List<DocumentNode>.namedDirectory(name: String): DocumentNode? =
    filter { it.name == name && it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
        .singleOrNull()

private fun List<DocumentNode>.singleNamedFile(name: String): DocumentNode? =
    filter { it.name == name && it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
        .singleOrNull()

private fun List<DocumentNode>.hasDuplicate(name: String): Boolean =
    count { it.name == name } > 1

private fun List<DocumentNode>.hasDuplicateNames(): Boolean =
    map { it.name }.distinct().size != size
