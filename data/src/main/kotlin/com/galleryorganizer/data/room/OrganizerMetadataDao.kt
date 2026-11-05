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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFileFact(entity: FileFactEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertBatchMembers(entities: List<BatchMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEditingDraft(entity: DraftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertInactiveDraft(entity: DraftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDraftAction(entity: DraftActionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertDraftActions(entities: List<DraftActionEntity>)

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

    @Query("SELECT * FROM draft_actions WHERE draftId = :draftId ORDER BY fileId")
    fun findDraftActions(draftId: String): List<DraftActionEntity>

    @Query("SELECT * FROM image_assets WHERE imageId = :imageId")
    fun findImageAsset(imageId: String): ImageAssetEntity?

    @Query("SELECT * FROM file_facts WHERE fileId = :fileId")
    fun findFileFact(fileId: String): FileFactEntity?

    @Query("SELECT * FROM file_facts WHERE rootId = :rootId AND relativePath = :relativePath")
    fun findFileFactByLocation(rootId: String, relativePath: String): FileFactEntity?

    @Query("SELECT * FROM source_decisions WHERE sourceKey = :sourceKey")
    fun findSourceDecision(sourceKey: String): SourceDecisionEntity?

    @Query("SELECT * FROM import_records WHERE sourceFileId = :sourceFileId")
    fun findImportsBySourceFileId(sourceFileId: String): List<ImportRecordEntity>

    @Query(
        """
        SELECT batch_members.batchId, file_facts.fileId, file_facts.relativePath,
            file_facts.displayName, file_facts.sizeBytes, file_facts.mtimeNormalized,
            file_facts.scanGeneration, file_facts.mimeType
        FROM batch_members
        INNER JOIN file_facts ON file_facts.fileId = batch_members.fileId
        WHERE batch_members.batchId IN (:batchIds)
        ORDER BY batch_members.batchId, file_facts.relativePath,
            file_facts.displayName, file_facts.fileId
        """,
    )
    fun findBatchFileFacts(batchIds: Set<String>): List<BatchFileFactRow>

    @Query(
        """
        SELECT batch_members.batchId, file_facts.fileId, file_facts.relativePath,
            file_facts.displayName, file_facts.sizeBytes, file_facts.mtimeNormalized,
            file_facts.scanGeneration, file_facts.mimeType
        FROM batch_members
        INNER JOIN file_facts ON file_facts.fileId = batch_members.fileId
        WHERE batch_members.batchId = :batchId
            AND (:afterFileId IS NULL OR file_facts.fileId > :afterFileId)
        ORDER BY file_facts.fileId
        LIMIT :limit
        """,
    )
    fun findBatchFileFactsPage(
        batchId: String,
        afterFileId: String?,
        limit: Int,
    ): List<BatchFileFactRow>

    @Query(
        """
        SELECT batchId AS batchKey, COUNT(*) AS imageCount
        FROM batch_members
        WHERE batchId LIKE 'directory:%'
            OR batchId = 'virtual:download-root-loose-images'
        GROUP BY batchId
        ORDER BY batchId
        LIMIT 256
        """,
    )
    fun findBatchSummaries(): List<BatchSummaryRow>

    @Query("SELECT * FROM asset_files WHERE imageId = :imageId AND fileId = :fileId")
    fun findAssetFile(imageId: String, fileId: String): AssetFileEntity?

    @Query("SELECT * FROM album_memberships WHERE albumId = :albumId AND imageId = :imageId")
    fun findAlbumMembership(albumId: String, imageId: String): AlbumMembershipEntity?

    @Query("SELECT * FROM plans WHERE planId = :planId")
    fun findPlan(planId: String): PlanEntity?

    @Transaction
    fun createSelectionDraft(draft: DraftEntity, actions: List<DraftActionEntity>) {
        insertEditingDraft(draft)
        insertDraftActions(actions)
    }

    @Transaction
    fun replaceSelectionDraft(
        draft: DraftEntity,
        expectedRevision: Long,
        actions: List<DraftActionEntity>,
    ): Boolean {
        if (updateDraftRevision(draft.draftId, expectedRevision, draft.revision) != 1) return false
        deleteDraftActions(draft.draftId)
        insertDraftActions(actions)
        invalidatePlansForDraft(draft.draftId)
        return true
    }

    @Transaction
    fun insertPlanForRevision(plan: PlanEntity): Boolean {
        if (countPlansForRevision(plan.draftId, plan.draftRevision) != 0) return false
        insertPlan(plan)
        return true
    }

    @Query(
        """
        UPDATE drafts SET revision = :nextRevision
        WHERE draftId = :draftId AND state = 'EDITING' AND revision = :expectedRevision
        """,
    )
    fun updateDraftRevision(draftId: String, expectedRevision: Long, nextRevision: Long): Int

    @Query("DELETE FROM draft_actions WHERE draftId = :draftId")
    fun deleteDraftActions(draftId: String): Int

    @Query(
        """
        UPDATE plans SET state = 'STALE'
        WHERE draftId = :draftId AND state IN ('CREATED', 'READY')
        """,
    )
    fun invalidatePlansForDraft(draftId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM plans
        WHERE draftId IS :draftId AND draftRevision IS :draftRevision
        """,
    )
    fun countPlansForRevision(draftId: String?, draftRevision: Long?): Int

    @Transaction
    fun persistSourceSnapshot(
        fileFacts: List<FileFactEntity>,
        batchMembers: List<BatchMemberEntity>,
        replaceCompletedSnapshot: Boolean,
    ) {
        if (replaceCompletedSnapshot) deleteDownloadBatchMembers()
        fileFacts.forEach { fact ->
            findFileFactByLocation(fact.rootId, fact.relativePath)
                ?.takeIf { it.fileId != fact.fileId }
                ?.let {
                    deleteBatchMembershipsForFile(it.fileId)
                    deleteFileFact(it.fileId)
                }
            deleteBatchMembershipsForFile(fact.fileId)
            upsertFileFact(fact)
        }
        insertBatchMembers(batchMembers)
    }

    @Transaction
    fun saveSourceDecision(entity: SourceDecisionEntity) {
        if (updateSourceDecision(entity) == 0) insertSourceDecision(entity)
    }

    @Query(
        """
        DELETE FROM batch_members
        WHERE batchId LIKE 'directory:%'
            OR batchId = 'virtual:download-root-loose-images'
        """,
    )
    fun deleteDownloadBatchMembers(): Int

    @Query("DELETE FROM batch_members WHERE fileId = :fileId")
    fun deleteBatchMembershipsForFile(fileId: String): Int

    @Query("DELETE FROM file_facts WHERE fileId = :fileId")
    fun deleteFileFact(fileId: String): Int

    @Query(
        """
        UPDATE source_decisions
        SET decision = :decision, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE sourceKey = :sourceKey
        """,
    )
    fun updateSourceDecision(
        sourceKey: String,
        decision: String,
        updatedAtEpochMillis: Long,
    ): Int

    fun updateSourceDecision(entity: SourceDecisionEntity): Int = updateSourceDecision(
        entity.sourceKey,
        entity.decision,
        entity.updatedAtEpochMillis,
    )

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
