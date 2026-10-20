package com.galleryorganizer.data.room

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
