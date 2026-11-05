package com.galleryorganizer.domain

import com.galleryorganizer.contract.WorkspaceAdoptionSnapshot
import com.galleryorganizer.contract.WorkspaceAlbumEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkspaceAdoptionTest {
    private val root = """{"schemaVersion":1,"workspaceId":"workspace-1"}"""
    private val committed = """
        {"schemaVersion":1,"albumId":"a","state":"COMMITTED",
        "createdAtEpochMillis":1,"committedAtEpochMillis":2,"images":[]}
    """.trimIndent()
    private val importing = """
        {"schemaVersion":1,"albumId":"b","state":"IMPORTING",
        "createdAtEpochMillis":1,"committedAtEpochMillis":null,"images":[]}
    """.trimIndent()

    @Test
    fun `valid marker and manifests are accepted with recovery count`() {
        val result = WorkspaceAdoptionValidator.validate(
            WorkspaceAdoptionSnapshot(
                root,
                listOf(
                    WorkspaceAlbumEvidence("2026-01-01__a", committed),
                    WorkspaceAlbumEvidence("2026-01-02__b", importing),
                ),
                true,
                false,
                false,
            ),
        )

        assertEquals(
            listOf("a", "b"),
            assertIs<WorkspaceAdoptionResult.Verified>(result)
                .albums.map { it.manifest.albumId },
        )
        assertEquals(
            listOf("2026-01-01__a", "2026-01-02__b"),
            result.albums.map(WorkspaceAlbumCatalogItem::directoryName),
        )
        assertEquals(
            WorkspaceAdoptionResult.Verified(
                albumCount = 2,
                importingCount = 1,
                albums = result.albums,
            ),
            result,
        )
    }

    @Test
    fun `missing malformed and incomplete evidence fails closed`() {
        val cases = listOf(
            WorkspaceAdoptionSnapshot(null, emptyList(), true, false, false) to
                WorkspaceAdoptionFailure.ROOT_MARKER_MISSING,
            WorkspaceAdoptionSnapshot("{}", emptyList(), true, false, false) to
                WorkspaceAdoptionFailure.ROOT_MARKER_INVALID,
            WorkspaceAdoptionSnapshot(
                root, listOf(WorkspaceAlbumEvidence("2026-01-01__a", null)),
                true, false, false,
            ) to
                WorkspaceAdoptionFailure.ALBUM_MANIFEST_MISSING,
            WorkspaceAdoptionSnapshot(
                root, listOf(WorkspaceAlbumEvidence("2026-01-01__a", "{}")),
                true, false, false,
            ) to
                WorkspaceAdoptionFailure.ALBUM_MANIFEST_INVALID,
            WorkspaceAdoptionSnapshot(
                root, listOf(WorkspaceAlbumEvidence("2026-01-01__wrong", committed)),
                true, false, false,
            ) to
                WorkspaceAdoptionFailure.ALBUM_ID_MISMATCH,
            WorkspaceAdoptionSnapshot(root, emptyList(), false, false, false) to
                WorkspaceAdoptionFailure.ALBUMS_DIRECTORY_MISSING,
            WorkspaceAdoptionSnapshot(root, emptyList(), true, true, false) to
                WorkspaceAdoptionFailure.WORKSPACE_CONFLICT,
            WorkspaceAdoptionSnapshot(root, emptyList(), true, false, true) to
                WorkspaceAdoptionFailure.EVIDENCE_LIMIT_EXCEEDED,
        )

        cases.forEach { (snapshot, expected) ->
            assertEquals(
                expected,
                assertIs<WorkspaceAdoptionResult.Rejected>(
                    WorkspaceAdoptionValidator.validate(snapshot),
                ).failure,
            )
        }
    }
}
