package com.galleryorganizer.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.galleryorganizer.domain.MetadataPayload
import com.galleryorganizer.domain.OrganizerPlan
import com.galleryorganizer.domain.PlanSnapshot
import com.galleryorganizer.domain.PlanStep
import com.galleryorganizer.domain.PlanStepType
import com.galleryorganizer.domain.PlanType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomMetadataPlanRepositoryTest {
    private lateinit var database: OrganizerMetadataDatabase
    private lateinit var repository: RoomMetadataPlanRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrganizerMetadataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomMetadataPlanRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `metadata plan persists as created without draft or schema change`() = runBlocking {
        val plan = metadataPlan()

        assertTrue(repository.save(plan))
        val stored = database.metadataDao().findPlan("metadata-1")!!

        assertEquals("METADATA", stored.type)
        assertEquals("CREATED", stored.state)
        assertEquals(null, stored.draftId)
        assertTrue(stored.payloadJson.contains("\\\"imageOrder\\\""))
        assertFalse(repository.save(plan))
    }

    private fun metadataPlan() = OrganizerPlan(
        "metadata-1",
        PlanType.METADATA,
        PlanSnapshot(20, null),
        30,
        MetadataPayload("album-1", """{"imageOrder":["b","a"]}"""),
        listOf(
            PlanStep(0, PlanStepType.APPLY_MANIFEST_PATCH, "album-1"),
            PlanStep(1, PlanStepType.COMMIT_MANIFEST, "album-1"),
        ),
    )
}
