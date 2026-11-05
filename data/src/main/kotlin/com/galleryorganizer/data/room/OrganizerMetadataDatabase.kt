package com.galleryorganizer.data.room

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ImageAssetEntity::class,
        FileFactEntity::class,
        AlbumMembershipEntity::class,
        UserStateEntity::class,
        AssetFileEntity::class,
        ImportRecordEntity::class,
        SourceDecisionEntity::class,
        EvidenceEntity::class,
        BatchMemberEntity::class,
        DraftEntity::class,
        DraftActionEntity::class,
        PlanEntity::class,
        JournalEntity::class,
        JournalStepEntity::class,
        TrashEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OrganizerMetadataDatabase : RoomDatabase() {
    abstract fun metadataDao(): OrganizerMetadataDao
}

private object MetadataDatabaseHolder {
    @Volatile private var instance: OrganizerMetadataDatabase? = null

    fun get(context: Context): OrganizerMetadataDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            OrganizerMetadataDatabase::class.java,
            "organizer-metadata.db",
        ).build().also { instance = it }
    }
}

internal fun organizerMetadataDatabase(context: Context): OrganizerMetadataDatabase =
    MetadataDatabaseHolder.get(context)
