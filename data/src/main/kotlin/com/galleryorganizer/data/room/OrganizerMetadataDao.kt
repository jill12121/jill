package com.galleryorganizer.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
    fun insertDraft(entity: DraftEntity)

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
