package com.galleryorganizer.data.saf

import androidx.test.core.app.ApplicationProvider
import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.StorageFailure
import com.galleryorganizer.contract.WorkspaceRootMode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class SafWorkspaceRootGatewayTest {
    @Test
    fun `non tree URI is rejected before persistence`() = runBlocking {
        val gateway = SafWorkspaceRootGateway(ApplicationProvider.getApplicationContext())

        val result = gateway.persistSelection(
            "file:///storage/emulated/0/Pictures",
            WorkspaceRootMode.NEW,
        )

        assertEquals(
            StorageFailure.NotFound,
            assertIs<FsResult.Failure>(result).failure,
        )
    }

    @Test
    fun `inspection without persisted selection fails closed`() = runBlocking {
        val gateway = SafWorkspaceRootGateway(ApplicationProvider.getApplicationContext())

        assertEquals(
            StorageFailure.NotFound,
            assertIs<FsResult.Failure>(gateway.inspectSelection()).failure,
        )
    }
}
