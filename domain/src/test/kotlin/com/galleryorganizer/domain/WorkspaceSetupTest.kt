package com.galleryorganizer.domain

import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.StorageFailure
import com.galleryorganizer.contract.WorkspaceRootGateway
import com.galleryorganizer.contract.WorkspaceRootMode
import com.galleryorganizer.contract.WorkspaceRootSelection
import com.galleryorganizer.contract.WorkspaceAdoptionSnapshot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkspaceSetupTest {
    @Test
    fun `accepted selection preserves gateway metadata`() = runBlocking {
        val selected = WorkspaceRootSelection(
            uri = "content://provider/tree/root",
            displayName = "GalleryOrganizer",
            relativePath = "Download/GalleryOrganizer",
            mode = WorkspaceRootMode.NEW,
        )
        val result = DefaultWorkspaceSetup(FakeRootGateway(FsResult.Success(selected)))
            .select(selected.uri, WorkspaceSetupMode.NEW)

        assertEquals(
            WorkspaceRoot(
                "GalleryOrganizer",
                "Download/GalleryOrganizer",
                WorkspaceSetupMode.NEW,
            ),
            assertIs<WorkspaceSetupResult.Accepted>(result).root,
        )
    }

    @Test
    fun `storage failure is mapped to workspace write failure`() = runBlocking {
        val result = DefaultWorkspaceSetup(
            FakeRootGateway(FsResult.Failure(StorageFailure.AccessDenied)),
        ).select("content://provider/tree/root", WorkspaceSetupMode.ADOPT_EXISTING)

        val rejected = assertIs<WorkspaceSetupResult.Rejected>(result)
        assertEquals(StorageOperation.WORKSPACE_WRITE, rejected.failure.operation)
        assertEquals(StorageFailure.AccessDenied, rejected.failure.failure)
    }

    @Test
    fun `adoption validation delegates bounded evidence to domain validator`() = runBlocking {
        val gateway = FakeRootGateway(
            result = FsResult.Failure(StorageFailure.NotFound),
            adoption = FsResult.Success(
                WorkspaceAdoptionSnapshot(
                    rootMarkerJson = """{"schemaVersion":1,"workspaceId":"w"}""",
                    albums = emptyList(),
                    albumsDirectoryPresent = true,
                    hasWorkspaceConflict = false,
                    truncated = false,
                ),
            ),
        )

        assertEquals(
            WorkspaceAdoptionResult.Verified(0, 0),
            DefaultWorkspaceSetup(gateway).validateAdoption(),
        )
    }
}

private class FakeRootGateway(
    private val result: FsResult<WorkspaceRootSelection>,
    private val adoption: FsResult<WorkspaceAdoptionSnapshot> =
        FsResult.Failure(StorageFailure.NotFound),
) : WorkspaceRootGateway {
    override suspend fun persistSelection(
        uri: String,
        mode: WorkspaceRootMode,
    ): FsResult<WorkspaceRootSelection> = result

    override suspend fun loadSelection(): FsResult<WorkspaceRootSelection?> =
        FsResult.Success(null)

    override suspend fun inspectSelection(): FsResult<WorkspaceAdoptionSnapshot> = adoption
}
