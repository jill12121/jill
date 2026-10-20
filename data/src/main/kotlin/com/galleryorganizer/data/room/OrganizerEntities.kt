package com.galleryorganizer.data.room

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "image_assets", primaryKeys = ["imageId"])
data class ImageAssetEntity(
    val imageId: String,
    val createdAtEpochMillis: Long,
    val createdByOperationId: String,
)

@Entity(
    tableName = "file_facts",
    primaryKeys = ["fileId"],
    indices = [
        Index(
            value = ["rootId", "relativePath"],
            unique = true,
            name = "index_file_facts_root_id_relative_path",
        ),
    ],
)
data class FileFactEntity(
    val fileId: String,
    val kind: String,
    val rootId: String,
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val mtimeNormalized: Long,
    val mtimePrecision: String,
    val mimeType: String?,
    val decodeState: String,
    val scanGeneration: Long,
    val fingerprintMethod: String,
    val quickFingerprint: String?,
    val sha256: String?,
    val contentKey: String,
)

@Entity(tableName = "album_memberships", primaryKeys = ["albumId", "imageId"])
data class AlbumMembershipEntity(
    val albumId: String,
    val imageId: String,
    val displayOrder: Int,
)

@Entity(tableName = "user_states", primaryKeys = ["imageId"])
data class UserStateEntity(val imageId: String, val decision: String)

@Entity(tableName = "asset_files", primaryKeys = ["imageId", "fileId"])
data class AssetFileEntity(
    val imageId: String,
    val fileId: String,
    val relation: String,
)

@Entity(
    tableName = "import_records",
    primaryKeys = ["importId"],
    indices = [
        Index(value = ["sourceFileId"], name = "index_import_records_source_file_id"),
        Index(value = ["targetFileId"], name = "index_import_records_target_file_id"),
    ],
)
data class ImportRecordEntity(
    val importId: String,
    val sourceFileId: String,
    val targetFileId: String,
    val imageId: String,
    val disposition: String,
    val sourceLocator: String,
    val sha256: String,
    val state: String,
    val verifiedAtEpochMillis: Long?,
)

@Entity(tableName = "source_decisions", primaryKeys = ["sourceKey"])
data class SourceDecisionEntity(
    val sourceKey: String,
    val decision: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "evidence",
    primaryKeys = ["evidenceId"],
    indices = [
        Index(
            value = ["contentKey", "type", "analysisVersion", "evidenceKey"],
            unique = true,
            name = "index_evidence_stable_identity",
        ),
    ],
)
data class EvidenceEntity(
    val evidenceId: String,
    val contentKey: String,
    val type: String,
    val score: Double,
    val maskedDetail: String,
    val analysisVersion: Int,
    val evidenceKey: String,
)

@Entity(tableName = "batch_members", primaryKeys = ["batchId", "fileId"])
data class BatchMemberEntity(val batchId: String, val fileId: String)

@Entity(
    tableName = "drafts",
    primaryKeys = ["draftId"],
    indices = [
        Index(value = ["activeKey"], unique = true, name = "index_drafts_active_key"),
    ],
)
data class DraftEntity(
    val draftId: String,
    val scopeType: String,
    val scopeId: String,
    val revision: Long,
    val activeKey: String?,
    val state: String,
) {
    init {
        require(state == "EDITING" || state == "STALE" || state == "ARCHIVED") {
            "Unsupported Draft state: $state"
        }
        require((state == "EDITING") == (activeKey != null)) {
            "Only EDITING Draft may have a non-null activeKey."
        }
    }
}

@Entity(tableName = "draft_actions", primaryKeys = ["draftId", "fileId"])
data class DraftActionEntity(
    val draftId: String,
    val fileId: String,
    val action: String,
    val displayOrder: Int?,
    val isCover: Boolean?,
)

@Entity(
    tableName = "plans",
    primaryKeys = ["planId"],
    indices = [
        Index(value = ["draftId", "type"], name = "index_plans_draft_id_type"),
    ],
)
data class PlanEntity(
    val planId: String,
    val draftId: String?,
    val type: String,
    val draftRevision: Long?,
    val scanSnapshot: String,
    val payloadJson: String,
    val state: String,
)

@Entity(
    tableName = "journals",
    primaryKeys = ["operationId"],
    indices = [Index(value = ["planId"], name = "index_journals_plan_id")],
)
data class JournalEntity(
    val operationId: String,
    val planId: String,
    val state: String,
    val diskJournalPath: String,
    val watermark: Long,
)

@Entity(tableName = "journal_steps", primaryKeys = ["operationId", "stepIndex"])
data class JournalStepEntity(
    val operationId: String,
    val stepIndex: Int,
    val type: String,
    val expectedFingerprint: String,
    val state: String,
    val undoInfo: String?,
)

@Entity(
    tableName = "trash_entries",
    primaryKeys = ["trashEntryId"],
    indices = [Index(value = ["fileId"], name = "index_trash_entries_file_id")],
)
data class TrashEntryEntity(
    val trashEntryId: String,
    val fileId: String,
    val imageId: String?,
    val sidecarPath: String,
    val expiresAtEpochMillis: Long,
    val state: String,
)
