package com.galleryorganizer.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OrganizerMetadataMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrganizerMetadataDatabase::class.java,
    )

    @Test
    fun `version one exported schema opens and validates`() {
        helper.createDatabase(TEST_DATABASE, 1).close()
        helper.runMigrationsAndValidate(TEST_DATABASE, 1, true).close()
    }

    private companion object {
        const val TEST_DATABASE = "organizer-migration-test"
    }
}
