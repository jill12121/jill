package com.galleryorganizer.domain

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AlbumEditorTest {
    @Test
    fun `changed committed image order creates a valid metadata plan`() = runBlocking {
        val repository = RecordingMetadataPlanRepository()
        val editor = DefaultAlbumEditor(repository)

        val result = editor.create(request(album()))

        val plan = assertIs<AlbumEditResult.Success>(result).preview.plan
        assertEquals("metadata:album-1:20", plan.planId)
        assertEquals(PlanType.METADATA, plan.type)
        assertEquals(20, plan.snapshot.scanGeneration)
        assertEquals("""{"imageOrder":["b","a"]}""", (plan.payload as MetadataPayload).manifestPatch)
        assertEquals(
            listOf(PlanStepType.APPLY_MANIFEST_PATCH, PlanStepType.COMMIT_MANIFEST),
            plan.steps.map(PlanStep::type),
        )
        assertEquals(listOf(plan), repository.saved)
    }

    @Test
    fun `unchanged invalid and importing orders fail without persistence`() = runBlocking {
        val repository = RecordingMetadataPlanRepository()
        val editor = DefaultAlbumEditor(repository)
        val committed = album()

        assertEquals(
            AlbumEditFailure.UNCHANGED_ORDER,
            assertIs<AlbumEditResult.Rejected>(
                editor.create(request(committed, listOf("a", "b"))),
            ).failure,
        )
        assertEquals(
            AlbumEditFailure.INVALID_ORDER,
            assertIs<AlbumEditResult.Rejected>(
                editor.create(request(committed, listOf("a", "missing"))),
            ).failure,
        )
        assertEquals(
            AlbumEditFailure.ALBUM_NOT_COMMITTED,
            assertIs<AlbumEditResult.Rejected>(
                editor.create(
                    request(
                        committed.copy(
                            manifest = committed.manifest.copy(
                                state = AlbumState.IMPORTING,
                                committedAtEpochMillis = null,
                            ),
                        ),
                    ),
                ),
            ).failure,
        )
        assertEquals(emptyList(), repository.saved)
    }

    private fun request(
        album: WorkspaceAlbumCatalogItem,
        order: List<String> = listOf("b", "a"),
    ) = AlbumEditRequest(album, order, 30)

    private fun album() = WorkspaceAlbumCatalogItem(
        "2026-01-01__album-1",
        AlbumManifest(
            1,
            "album-1",
            AlbumState.COMMITTED,
            10,
            20,
            listOf(image("a", 0), image("b", 1)),
        ),
    )

    private fun image(id: String, order: Int) =
        ManifestImage(id, "$id.jpg", order, 1, 2, "sha256", id)
}

private class RecordingMetadataPlanRepository : MetadataPlanRepository {
    val saved = mutableListOf<OrganizerPlan>()

    override suspend fun save(plan: OrganizerPlan): Boolean {
        saved += plan
        return true
    }
}
