package com.galleryorganizer.data.mediastore

import androidx.test.core.app.ApplicationProvider
import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.StorageFailure
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class MediaStoreSourceDiscoveryGatewayTest {
    private val gateway = MediaStoreSourceDiscoveryGateway(
        ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
    )

    @Test
    fun `page bounds fail before querying provider`() = runBlocking {
        val oversized = gateway.discoverPage("Download", afterId = null, limit = 501)
        val negativeCursor = gateway.discoverPage("Download", afterId = -1, limit = 500)

        assertEquals(StorageFailure.Conflict, assertIs<FsResult.Failure>(oversized).failure)
        assertEquals(StorageFailure.Conflict, assertIs<FsResult.Failure>(negativeCursor).failure)
    }
}
