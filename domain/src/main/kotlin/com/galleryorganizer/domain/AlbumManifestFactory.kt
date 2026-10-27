package com.galleryorganizer.domain

/**
 * Pure construction and state transitions for album manifests. No persistence;
 * mirrors the IMPORTING -> COMMITTED archive sequence in the domain state
 * machine so later executors cannot reach COMMITTED through an invalid path.
 */
object AlbumManifestFactory {
    fun importing(
        albumId: String,
        createdAtEpochMillis: Long,
        images: List<ManifestImage>,
    ): AlbumManifest {
        require(albumId.isNotBlank()) { "Album id is required." }
        require(createdAtEpochMillis > 0) { "Creation time must be positive." }
        return AlbumManifest(
            schemaVersion = AlbumManifestCodec.SUPPORTED_SCHEMA_VERSION,
            albumId = albumId,
            state = AlbumState.IMPORTING,
            createdAtEpochMillis = createdAtEpochMillis,
            committedAtEpochMillis = null,
            images = images.sortedBy(ManifestImage::order),
        )
    }

    fun commit(
        importing: AlbumManifest,
        committedAtEpochMillis: Long,
    ): AlbumManifest {
        require(importing.state == AlbumState.IMPORTING) {
            "Only IMPORTING manifests can be committed."
        }
        require(committedAtEpochMillis >= importing.createdAtEpochMillis) {
            "Commit time must not precede creation time."
        }
        check(
            OrganizerStateMachines.canTransition(
                AlbumState.IMPORTING, AlbumState.COMMITTED,
            ),
        ) { "IMPORTING to COMMITTED is not a legal album transition." }
        return importing.copy(
            state = AlbumState.COMMITTED,
            committedAtEpochMillis = committedAtEpochMillis,
        )
    }
}
