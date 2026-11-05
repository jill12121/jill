package com.galleryorganizer.domain

import com.galleryorganizer.contract.WorkspaceAdoptionSnapshot

enum class WorkspaceAdoptionFailure {
    ROOT_MARKER_MISSING,
    ROOT_MARKER_INVALID,
    ALBUMS_DIRECTORY_MISSING,
    ALBUM_MANIFEST_MISSING,
    ALBUM_MANIFEST_INVALID,
    ALBUM_ID_MISMATCH,
    WORKSPACE_CONFLICT,
    EVIDENCE_LIMIT_EXCEEDED,
    STORAGE_UNAVAILABLE,
}

sealed interface WorkspaceAdoptionResult {
    data class Verified(
        val albumCount: Int,
        val importingCount: Int,
        val albums: List<WorkspaceAlbumCatalogItem> = emptyList(),
    ) : WorkspaceAdoptionResult

    data class Rejected(val failure: WorkspaceAdoptionFailure) : WorkspaceAdoptionResult
}

data class WorkspaceAlbumCatalogItem(
    val directoryName: String,
    val manifest: AlbumManifest,
)

object WorkspaceAdoptionValidator {
    fun validate(snapshot: WorkspaceAdoptionSnapshot): WorkspaceAdoptionResult {
        if (snapshot.truncated) return rejected(WorkspaceAdoptionFailure.EVIDENCE_LIMIT_EXCEEDED)
        if (snapshot.hasWorkspaceConflict) {
            return rejected(WorkspaceAdoptionFailure.WORKSPACE_CONFLICT)
        }
        if (!snapshot.albumsDirectoryPresent) {
            return rejected(WorkspaceAdoptionFailure.ALBUMS_DIRECTORY_MISSING)
        }
        val rootJson = snapshot.rootMarkerJson
            ?: return rejected(WorkspaceAdoptionFailure.ROOT_MARKER_MISSING)
        if (!validRootMarker(rootJson)) {
            return rejected(WorkspaceAdoptionFailure.ROOT_MARKER_INVALID)
        }

        var importingCount = 0
        val albumIds = mutableSetOf<String>()
        val albums = mutableListOf<WorkspaceAlbumCatalogItem>()
        snapshot.albums.forEach { album ->
            val json = album.manifestJson
            if (json == null) {
                return rejected(WorkspaceAdoptionFailure.ALBUM_MANIFEST_MISSING)
            }
            when (val decoded = AlbumManifestCodec.decode(json)) {
                is ManifestDecodeResult.Invalid ->
                    return rejected(WorkspaceAdoptionFailure.ALBUM_MANIFEST_INVALID)
                is ManifestDecodeResult.Valid -> {
                    if (!album.directoryName.endsWith("__${decoded.manifest.albumId}") ||
                        !albumIds.add(decoded.manifest.albumId)
                    ) {
                        return rejected(WorkspaceAdoptionFailure.ALBUM_ID_MISMATCH)
                    }
                    if (decoded.manifest.state == AlbumState.IMPORTING) importingCount++
                    albums += WorkspaceAlbumCatalogItem(album.directoryName, decoded.manifest)
                }
            }
        }
        return WorkspaceAdoptionResult.Verified(
            albumCount = snapshot.albums.size,
            importingCount = importingCount,
            albums = albums,
        )
    }

    private fun validRootMarker(json: String): Boolean {
        val root = runCatching { MiniJson.parse(json) }.getOrNull() as? Map<*, *> ?: return false
        return root["schemaVersion"] == 1L &&
            (root["workspaceId"] as? String)?.isNotBlank() == true
    }

    private fun rejected(failure: WorkspaceAdoptionFailure) =
        WorkspaceAdoptionResult.Rejected(failure)
}
