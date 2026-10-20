package com.galleryorganizer.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface OrganizerMetadataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertImageAsset(entity: ImageAssetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFileFact(entity: FileFactEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAlbumMembership(entity: AlbumMembershipEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertUserState(entity: UserStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertAssetFile(entity: AssetFileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertImportRecord(entity: ImportRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSourceDecision(entity: SourceDecisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEvidence(entity: EvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBatchMember(entity: BatchMemberEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEditingDraft(entity: DraftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertInactiveDraft(entity: DraftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDraftAction(entity: DraftActionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertPlan(entity: PlanEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertJournal(entity: JournalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertJournalStep(entity: JournalStepEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTrashEntry(entity: TrashEntryEntity)

    @Query("SELECT * FROM import_records WHERE importId = :importId")
    fun findImportRecord(importId: String): ImportRecordEntity?

    @Query("SELECT * FROM drafts WHERE draftId = :draftId")
    fun findDraft(draftId: String): DraftEntity?

    @Query("SELECT * FROM image_assets WHERE imageId = :imageId")
    fun findImageAsset(imageId: String): ImageAssetEntity?

    @Query("SELECT * FROM file_facts WHERE fileId = :fileId")
    fun findFileFact(fileId: String): FileFactEntity?

    @Query("SELECT * FROM asset_files WHERE imageId = :imageId AND fileId = :fileId")
    fun findAssetFile(imageId: String, fileId: String): AssetFileEntity?

    @Query("SELECT * FROM album_memberships WHERE albumId = :albumId AND imageId = :imageId")
    fun findAlbumMembership(albumId: String, imageId: String): AlbumMembershipEntity?

    @Transaction
    fun insertCommittedImportMetadata(
        imageAsset: ImageAssetEntity,
        targetFile: FileFactEntity,
        assetFile: AssetFileEntity,
        membership: AlbumMembershipEntity,
        importRecord: ImportRecordEntity,
    ) {
        insertImageAsset(imageAsset)
        insertFileFact(targetFile)
        insertAssetFile(assetFile)
        insertAlbumMembership(membership)
        insertImportRecord(importRecord)
    }

    @Query(
        """
        UPDATE drafts
        SET state = 'STALE', activeKey = NULL
        WHERE draftId = :draftId AND state = 'EDITING'
        """,
    )
    fun invalidateDraft(draftId: String): Int

    @Query(
        """
        UPDATE drafts
        SET state = 'ARCHIVED', activeKey = NULL
        WHERE draftId = :draftId AND state = 'STALE'
        """,
    )
    fun archiveDraft(draftId: String): Int
}
