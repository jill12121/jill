package com.galleryorganizer.domain

import com.galleryorganizer.contract.FileFingerprint
import com.galleryorganizer.contract.RelativePath
import com.galleryorganizer.contract.Sha256
import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.StorageFailure

enum class DraftState { EDITING, STALE, ARCHIVED }
enum class PlanState {
    CREATED,
    READY,
    EXECUTING,
    WAITING_FOR_USER_AUTH,
    COMPLETED,
    PARTIAL,
    FAILED,
    STALE,
    BLOCKED,
}

enum class AlbumState { IMPORTING, COMMITTED, RECOVERY_REQUIRED }
enum class ImportRecordState {
    PENDING_COPY,
    COPIED,
    COMMITTED,
    COPIED_SOURCE_RETAINED,
    CLEANUP_PENDING,
    CLEANED,
    CLEANUP_DENIED,
    SOURCE_CHANGED,
    FAILED,
}

enum class TrashEntryState { ACTIVE, EXPIRED, DELETE_PLANNED, DELETED, RESTORED }
enum class SourceDecisionValue { KEEP, REVIEW, TRASH }
enum class PlanType { ORG, METADATA, TRASH_MOVE, PERMANENT_DELETE, SOURCE_CLEANUP }
enum class PlanStepType {
    CREATE_DIR,
    WRITE_NOMEDIA,
    WRITE_MANIFEST,
    COPY_PART,
    PROMOTE_PART,
    COMMIT_MANIFEST,
    APPLY_MANIFEST_PATCH,
    MOVE_TO_TRASH,
    WRITE_SIDECAR,
    VERIFY_SIDECAR,
    VERIFY_SIZE_MTIME,
    DELETE_TRASH_TARGET,
    VERIFY_TARGET_SHA,
    VERIFY_SOURCE_FINGERPRINT,
    REQUEST_USER_AUTH,
    REQUERY_SOURCE,
}

enum class ImportStrategy { COPY_KEEP_SOURCE, MOVE_SOURCE, INDEX_IN_PLACE }

data class PlanSnapshot(
    val scanGeneration: Long,
    val draftRevision: Long?,
)

data class PlanStep(
    val index: Int,
    val type: PlanStepType,
    val targetId: String,
)

data class SourceLocator(
    val ref: SourceFileRef,
    val fingerprint: FileFingerprint,
)

data class PlannedImport(
    val source: SourceLocator,
    val target: RelativePath,
    val strategy: ImportStrategy,
)

data class TrashMoveTarget(
    val source: RelativePath,
    val trashTarget: RelativePath,
    val sidecar: RelativePath,
    val expectedFingerprint: FileFingerprint,
)

data class PermanentDeleteTarget(
    val trashEntryId: String,
    val trashTarget: RelativePath,
    val sidecar: RelativePath,
    val expectedFingerprint: FileFingerprint,
)

data class SourceCleanupCandidate(
    val source: SourceLocator,
    val target: RelativePath,
    val expectedSha256: Sha256,
)

sealed interface PlanPayload {
    val planType: PlanType
}

data class OrgPayload(
    val albumId: String,
    val immutableDir: RelativePath,
    val imports: List<PlannedImport>,
) : PlanPayload {
    override val planType = PlanType.ORG
}

data class MetadataPayload(
    val albumId: String,
    val manifestPatch: String,
) : PlanPayload {
    override val planType = PlanType.METADATA
}

data class TrashMovePayload(
    val entries: List<TrashMoveTarget>,
) : PlanPayload {
    override val planType = PlanType.TRASH_MOVE
}

data class PermanentDeletePayload(
    val entries: List<PermanentDeleteTarget>,
) : PlanPayload {
    override val planType = PlanType.PERMANENT_DELETE
}

data class SourceCleanupPayload(
    val locatorChunks: List<List<SourceCleanupCandidate>>,
) : PlanPayload {
    override val planType = PlanType.SOURCE_CLEANUP
}

data class OrganizerPlan(
    val planId: String,
    val type: PlanType,
    val snapshot: PlanSnapshot,
    val createdAtEpochMillis: Long,
    val payload: PlanPayload,
    val steps: List<PlanStep>,
)

data class SourceDecision(
    val sourceKey: String,
    val decision: SourceDecisionValue,
    val updatedAtEpochMillis: Long,
) {
    fun appliesTo(currentSourceKey: String): Boolean = sourceKey == currentSourceKey
}

data class PlanSchemaIssue(val field: String, val message: String)

object PlanSchemas {
    fun validate(plan: OrganizerPlan): List<PlanSchemaIssue> = buildList {
        if (plan.planId.isBlank()) add(PlanSchemaIssue("planId", "Plan id is required."))
        if (plan.createdAtEpochMillis <= 0) add(PlanSchemaIssue("createdAt", "Creation time is required."))
        if (plan.snapshot.scanGeneration <= 0) {
            add(PlanSchemaIssue("snapshot.scanGeneration", "Scan generation must be positive."))
        }
        if (plan.steps.isEmpty()) add(PlanSchemaIssue("steps", "At least one step is required."))
        if (plan.payload.planType != plan.type) {
            add(PlanSchemaIssue("payload", "Payload type must match plan type."))
        }
        val expectedIndexes = plan.steps.indices.toList()
        val actualIndexes = plan.steps.map { it.index }
        if (actualIndexes != expectedIndexes) {
            add(PlanSchemaIssue("steps.index", "Step indexes must be contiguous from zero."))
        }
        if (plan.steps.any { it.targetId.isBlank() }) {
            add(PlanSchemaIssue("steps.targetId", "Step target id is required."))
        }

        val actualTypes = plan.steps.map { it.type }.toSet()
        val allowedTypes = allowedSteps.getValue(plan.type)
        if (actualTypes.any { it !in allowedTypes }) {
            add(PlanSchemaIssue("steps.type", "Plan contains a step that is invalid for its type."))
        }
        if (!actualTypes.containsAll(requiredSteps.getValue(plan.type))) {
            add(PlanSchemaIssue("steps.required", "Plan is missing required steps."))
        }

        when (val payload = plan.payload) {
            is OrgPayload -> {
                if (payload.albumId.isBlank()) add(PlanSchemaIssue("payload.albumId", "Album id is required."))
                if (payload.immutableDir.value.isBlank()) {
                    add(PlanSchemaIssue("payload.immutableDir", "Immutable directory is required."))
                }
                if (payload.imports.isEmpty()) add(PlanSchemaIssue("payload.imports", "Imports are required."))
                if (payload.imports.any { it.target.value.isBlank() }) {
                    add(PlanSchemaIssue("payload.imports.target", "Import target is required."))
                }
            }
            is MetadataPayload -> {
                if (payload.albumId.isBlank()) add(PlanSchemaIssue("payload.albumId", "Album id is required."))
                if (payload.manifestPatch.isBlank()) {
                    add(PlanSchemaIssue("payload.manifestPatch", "Manifest patch is required."))
                }
            }
            is TrashMovePayload -> {
                if (payload.entries.isEmpty()) add(PlanSchemaIssue("payload.entries", "Trash entries are required."))
            }
            is PermanentDeletePayload -> {
                if (payload.entries.isEmpty()) add(PlanSchemaIssue("payload.entries", "Delete entries are required."))
                if (payload.entries.any { it.trashEntryId.isBlank() }) {
                    add(PlanSchemaIssue("payload.entries.trashEntryId", "Trash entry id is required."))
                }
            }
            is SourceCleanupPayload -> {
                if (payload.locatorChunks.isEmpty() || payload.locatorChunks.any { it.isEmpty() }) {
                    add(PlanSchemaIssue("payload.locatorChunks", "Non-empty locator chunks are required."))
                }
                if (payload.locatorChunks.any { it.size > MAX_SOURCE_CLEANUP_CHUNK_SIZE }) {
                    add(PlanSchemaIssue("payload.locatorChunks", "Locator chunk exceeds the authorization limit."))
                }
            }
        }
    }

    private const val MAX_SOURCE_CLEANUP_CHUNK_SIZE = 300

    private val allowedSteps = mapOf(
        PlanType.ORG to setOf(
            PlanStepType.CREATE_DIR,
            PlanStepType.WRITE_NOMEDIA,
            PlanStepType.WRITE_MANIFEST,
            PlanStepType.COPY_PART,
            PlanStepType.PROMOTE_PART,
            PlanStepType.COMMIT_MANIFEST,
        ),
        PlanType.METADATA to setOf(PlanStepType.APPLY_MANIFEST_PATCH, PlanStepType.COMMIT_MANIFEST),
        PlanType.TRASH_MOVE to setOf(PlanStepType.MOVE_TO_TRASH, PlanStepType.WRITE_SIDECAR),
        PlanType.PERMANENT_DELETE to setOf(
            PlanStepType.VERIFY_SIDECAR,
            PlanStepType.VERIFY_SIZE_MTIME,
            PlanStepType.DELETE_TRASH_TARGET,
        ),
        PlanType.SOURCE_CLEANUP to setOf(
            PlanStepType.VERIFY_TARGET_SHA,
            PlanStepType.VERIFY_SOURCE_FINGERPRINT,
            PlanStepType.REQUEST_USER_AUTH,
            PlanStepType.REQUERY_SOURCE,
        ),
    )

    private val requiredSteps = allowedSteps
}

enum class DomainErrorCode(
    val userMessage: String,
    val action: String,
) {
    PARTIAL_MEDIA_ACCESS("扫描范围不完整", "扩大授权或只导入已选图片"),
    SOURCE_MISSING("原文件已不存在", "重新扫描/移除该项"),
    SOURCE_CHANGED("原文件已改变", "重新预演"),
    TOCTOU_MISMATCH("执行期间文件发生变化", "阻断该项，保留其他成功项"),
    TARGET_CONFLICT("目标名称冲突", "重新生成 ID 或修改计划"),
    STORAGE_FULL("存储空间不足", "清理空间或改索引原地"),
    WRITE_PERMISSION_LOST("整理目录权限失效", "重新选择目录"),
    SOURCE_DELETE_AUTH_DENIED("未授权清理原文件", "已归档，源文件保留"),
    MANIFEST_RECOVERY_REQUIRED("图集清单需要恢复", "打开恢复中心"),
    JOURNAL_CORRUPT("操作日志损坏", "从 IMPORTING/目录重建"),
    SIDECAR_MISMATCH("回收站文件已变更", "手动确认处理"),
    TRASH_RESTORE_CONFLICT("原位置不可恢复", "选择新位置"),
    NOTIFICATION_DENIED("后台通知不可用", "留在前台或稍后继续"),
}

enum class StorageOperation {
    SOURCE_ENUMERATION,
    SOURCE_READ,
    WORKSPACE_WRITE,
    MANIFEST_READ,
    MANIFEST_WRITE,
    TRASH_MOVE,
    TRASH_PURGE,
    TRASH_RESTORE,
    SOURCE_CLEANUP_AUTH,
}

sealed interface DomainFailure {
    data class Storage(
        val operation: StorageOperation,
        val failure: StorageFailure,
    ) : DomainFailure

    data object SourceChanged : DomainFailure
    data object ToctouMismatch : DomainFailure
    data object JournalCorrupt : DomainFailure
    data object SidecarMismatch : DomainFailure
    data object NotificationDenied : DomainFailure
}

sealed interface FailureResolution {
    data class UserAction(val code: DomainErrorCode) : FailureResolution
    data object Retry : FailureResolution
}

fun DomainFailure.resolve(): FailureResolution = when (this) {
    is DomainFailure.Storage -> when (failure) {
        StorageFailure.AccessDenied -> FailureResolution.UserAction(
            when (operation) {
                StorageOperation.SOURCE_ENUMERATION,
                StorageOperation.SOURCE_READ,
                -> DomainErrorCode.PARTIAL_MEDIA_ACCESS
                StorageOperation.SOURCE_CLEANUP_AUTH -> DomainErrorCode.SOURCE_DELETE_AUTH_DENIED
                else -> DomainErrorCode.WRITE_PERMISSION_LOST
            },
        )
        StorageFailure.NotFound -> FailureResolution.UserAction(
            when (operation) {
                StorageOperation.MANIFEST_READ,
                StorageOperation.MANIFEST_WRITE,
                -> DomainErrorCode.MANIFEST_RECOVERY_REQUIRED
                else -> DomainErrorCode.SOURCE_MISSING
            },
        )
        StorageFailure.Conflict -> FailureResolution.UserAction(
            if (operation == StorageOperation.TRASH_RESTORE) {
                DomainErrorCode.TRASH_RESTORE_CONFLICT
            } else {
                DomainErrorCode.TARGET_CONFLICT
            },
        )
        StorageFailure.InsufficientSpace -> FailureResolution.UserAction(DomainErrorCode.STORAGE_FULL)
        is StorageFailure.ProviderUnavailable -> FailureResolution.Retry
    }
    DomainFailure.SourceChanged -> FailureResolution.UserAction(DomainErrorCode.SOURCE_CHANGED)
    DomainFailure.ToctouMismatch -> FailureResolution.UserAction(DomainErrorCode.TOCTOU_MISMATCH)
    DomainFailure.JournalCorrupt -> FailureResolution.UserAction(DomainErrorCode.JOURNAL_CORRUPT)
    DomainFailure.SidecarMismatch -> FailureResolution.UserAction(DomainErrorCode.SIDECAR_MISMATCH)
    DomainFailure.NotificationDenied -> FailureResolution.UserAction(DomainErrorCode.NOTIFICATION_DENIED)
}

enum class MediaReadGrant { READ_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, VISUAL_USER_SELECTED }
enum class WorkspaceWriteGrant { SAF }
enum class NotificationGrant { NONE, POST_NOTIFICATIONS, POST_NOTIFICATIONS_WITH_FOREGROUND_POLICY }
enum class ScanCoverage { FULL, PARTIAL_USER_SELECTED, UNKNOWN }

data class PermissionMatrix(
    val readGrants: Set<MediaReadGrant>,
    val supportsPartialSelection: Boolean,
    val workspaceWriteGrant: WorkspaceWriteGrant,
    val notificationGrant: NotificationGrant,
)

data class ScanStatus(
    val coverage: ScanCoverage,
    val completed: Boolean,
) {
    fun canSweep(): Boolean = coverage == ScanCoverage.FULL && completed
    fun canOrganizeWholeBatch(): Boolean = canSweep()
    fun canImportExplicitSelection(): Boolean = coverage != ScanCoverage.UNKNOWN
}

fun permissionMatrixFor(apiLevel: Int): PermissionMatrix {
    require(apiLevel >= 30) { "Android API level must be at least 30." }
    return when {
        apiLevel in 30..32 -> PermissionMatrix(
        readGrants = setOf(MediaReadGrant.READ_EXTERNAL_STORAGE),
        supportsPartialSelection = false,
        workspaceWriteGrant = WorkspaceWriteGrant.SAF,
        notificationGrant = NotificationGrant.NONE,
        )
        apiLevel == 33 -> PermissionMatrix(
        readGrants = setOf(MediaReadGrant.READ_MEDIA_IMAGES),
        supportsPartialSelection = false,
        workspaceWriteGrant = WorkspaceWriteGrant.SAF,
        notificationGrant = NotificationGrant.POST_NOTIFICATIONS,
        )
        apiLevel == 34 -> PermissionMatrix(
        readGrants = setOf(MediaReadGrant.READ_MEDIA_IMAGES, MediaReadGrant.VISUAL_USER_SELECTED),
        supportsPartialSelection = true,
        workspaceWriteGrant = WorkspaceWriteGrant.SAF,
        notificationGrant = NotificationGrant.POST_NOTIFICATIONS,
        )
        else -> PermissionMatrix(
        readGrants = setOf(MediaReadGrant.READ_MEDIA_IMAGES, MediaReadGrant.VISUAL_USER_SELECTED),
        supportsPartialSelection = true,
        workspaceWriteGrant = WorkspaceWriteGrant.SAF,
        notificationGrant = NotificationGrant.POST_NOTIFICATIONS_WITH_FOREGROUND_POLICY,
        )
    }
}

object OrganizerStateMachines {
    fun canTransition(from: DraftState, to: DraftState): Boolean = when (from) {
        DraftState.EDITING -> to == DraftState.STALE
        DraftState.STALE -> to == DraftState.ARCHIVED
        DraftState.ARCHIVED -> false
    }

    fun canTransition(from: PlanState, to: PlanState): Boolean = when (from) {
        PlanState.CREATED -> to in setOf(PlanState.READY, PlanState.STALE, PlanState.BLOCKED)
        PlanState.READY -> to in setOf(PlanState.EXECUTING, PlanState.STALE, PlanState.BLOCKED)
        PlanState.EXECUTING -> to in setOf(
            PlanState.WAITING_FOR_USER_AUTH,
            PlanState.COMPLETED,
            PlanState.PARTIAL,
            PlanState.FAILED,
            PlanState.BLOCKED,
        )
        PlanState.WAITING_FOR_USER_AUTH -> to in setOf(
            PlanState.READY,
            PlanState.EXECUTING,
            PlanState.PARTIAL,
            PlanState.FAILED,
            PlanState.BLOCKED,
        )
        else -> false
    }

    fun canTransition(from: AlbumState, to: AlbumState): Boolean =
        from == AlbumState.IMPORTING && to in setOf(AlbumState.COMMITTED, AlbumState.RECOVERY_REQUIRED)

    fun canTransition(from: ImportRecordState, to: ImportRecordState): Boolean = when (from) {
        ImportRecordState.PENDING_COPY -> to == ImportRecordState.COPIED
        ImportRecordState.COPIED -> to in setOf(
            ImportRecordState.COMMITTED,
            ImportRecordState.COPIED_SOURCE_RETAINED,
            ImportRecordState.CLEANUP_PENDING,
            ImportRecordState.CLEANED,
            ImportRecordState.CLEANUP_DENIED,
            ImportRecordState.SOURCE_CHANGED,
            ImportRecordState.FAILED,
        )
        ImportRecordState.CLEANUP_PENDING -> to in setOf(
            ImportRecordState.CLEANED,
            ImportRecordState.CLEANUP_DENIED,
            ImportRecordState.SOURCE_CHANGED,
            ImportRecordState.FAILED,
        )
        else -> false
    }

    fun canTransition(from: TrashEntryState, to: TrashEntryState): Boolean = when (from) {
        TrashEntryState.ACTIVE -> to in setOf(TrashEntryState.EXPIRED, TrashEntryState.DELETE_PLANNED, TrashEntryState.RESTORED)
        TrashEntryState.EXPIRED -> to in setOf(TrashEntryState.DELETE_PLANNED, TrashEntryState.RESTORED)
        TrashEntryState.DELETE_PLANNED -> to == TrashEntryState.DELETED
        else -> false
    }
}
