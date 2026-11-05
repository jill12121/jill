package com.galleryorganizer.domain

import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.WorkspaceRootGateway
import com.galleryorganizer.contract.WorkspaceRootMode

interface Scanner {
    suspend fun scan(request: SourceScanRequest): SourceScanResult
}

data class AnalysisRequest(val subjectId: String)

data class AnalysisSummary(val subjectId: String)

interface Analyzer {
    suspend fun analyze(request: AnalysisRequest): AnalysisSummary
}

data class AlbumEditRequest(
    val album: WorkspaceAlbumCatalogItem,
    val orderedImageIds: List<String>,
    val createdAtEpochMillis: Long,
)

data class AlbumEditPreview(
    val albumId: String,
    val plan: OrganizerPlan,
)

enum class AlbumEditFailure {
    ALBUM_NOT_COMMITTED,
    INVALID_ORDER,
    UNCHANGED_ORDER,
    INVALID_PLAN,
    PERSISTENCE_CONFLICT,
}

sealed interface AlbumEditResult {
    data class Success(val preview: AlbumEditPreview) : AlbumEditResult
    data class Rejected(val failure: AlbumEditFailure) : AlbumEditResult
}

interface MetadataPlanRepository {
    suspend fun save(plan: OrganizerPlan): Boolean
}

interface AlbumEditor {
    fun preview(request: AlbumEditRequest): AlbumEditResult
    suspend fun create(request: AlbumEditRequest): AlbumEditResult
}

class DefaultAlbumEditor(
    private val repository: MetadataPlanRepository,
) : AlbumEditor {
    override fun preview(request: AlbumEditRequest): AlbumEditResult {
        val manifest = request.album.manifest
        if (manifest.state != AlbumState.COMMITTED) {
            return rejected(AlbumEditFailure.ALBUM_NOT_COMMITTED)
        }
        val currentOrder = manifest.images.sortedBy(ManifestImage::order)
            .map(ManifestImage::imageId)
        if (request.orderedImageIds.size != currentOrder.size ||
            request.orderedImageIds.toSet() != currentOrder.toSet()
        ) {
            return rejected(AlbumEditFailure.INVALID_ORDER)
        }
        if (request.orderedImageIds == currentOrder) {
            return rejected(AlbumEditFailure.UNCHANGED_ORDER)
        }
        val orderJson = request.orderedImageIds.joinToString(",") {
            """"${it.jsonEscaped()}""""
        }
        val patch = """{"imageOrder":[$orderJson]}"""
        val plan = OrganizerPlan(
            planId = "metadata:${manifest.albumId}:${manifest.committedAtEpochMillis}",
            type = PlanType.METADATA,
            snapshot = PlanSnapshot(manifest.committedAtEpochMillis ?: 0, null),
            createdAtEpochMillis = request.createdAtEpochMillis,
            payload = MetadataPayload(manifest.albumId, patch),
            steps = listOf(
                PlanStep(0, PlanStepType.APPLY_MANIFEST_PATCH, manifest.albumId),
                PlanStep(1, PlanStepType.COMMIT_MANIFEST, manifest.albumId),
            ),
        )
        if (PlanSchemas.validate(plan).isNotEmpty()) {
            return rejected(AlbumEditFailure.INVALID_PLAN)
        }
        return AlbumEditResult.Success(AlbumEditPreview(manifest.albumId, plan))
    }

    override suspend fun create(request: AlbumEditRequest): AlbumEditResult =
        when (val result = preview(request)) {
            is AlbumEditResult.Rejected -> result
            is AlbumEditResult.Success -> if (repository.save(result.preview.plan)) {
                result
            } else {
                rejected(AlbumEditFailure.PERSISTENCE_CONFLICT)
            }
        }

    private fun rejected(failure: AlbumEditFailure) = AlbumEditResult.Rejected(failure)
}

private fun String.jsonEscaped(): String = replace("\\", "\\\\").replace("\"", "\\\"")

data class SourceCleanupRequest(val sourceCount: Int)

data class SourceCleanupPreview(val sourceCount: Int)

interface SourceCleanup {
    suspend fun preview(request: SourceCleanupRequest): SourceCleanupPreview
}

enum class WorkspaceSetupMode { NEW, ADOPT_EXISTING }

data class WorkspaceRoot(
    val displayName: String,
    val relativePath: String?,
    val mode: WorkspaceSetupMode,
)

sealed interface WorkspaceSetupResult {
    data class Accepted(val root: WorkspaceRoot) : WorkspaceSetupResult
    data class Rejected(val failure: DomainFailure.Storage) : WorkspaceSetupResult
    data object NoSelection : WorkspaceSetupResult
}

interface WorkspaceSetup {
    suspend fun select(uri: String, mode: WorkspaceSetupMode): WorkspaceSetupResult
    suspend fun load(): WorkspaceSetupResult
    suspend fun validateAdoption(): WorkspaceAdoptionResult
}

class DefaultWorkspaceSetup(
    private val gateway: WorkspaceRootGateway,
) : WorkspaceSetup {
    override suspend fun select(uri: String, mode: WorkspaceSetupMode): WorkspaceSetupResult =
        when (val result = gateway.persistSelection(uri, mode.toContract())) {
            is FsResult.Success -> WorkspaceSetupResult.Accepted(result.value.toDomain())
            is FsResult.Failure -> WorkspaceSetupResult.Rejected(
                DomainFailure.Storage(StorageOperation.WORKSPACE_WRITE, result.failure),
            )
        }

    override suspend fun load(): WorkspaceSetupResult = when (val result = gateway.loadSelection()) {
        is FsResult.Success -> result.value?.let { WorkspaceSetupResult.Accepted(it.toDomain()) }
            ?: WorkspaceSetupResult.NoSelection
        is FsResult.Failure -> WorkspaceSetupResult.Rejected(
            DomainFailure.Storage(StorageOperation.WORKSPACE_WRITE, result.failure),
        )
    }

    override suspend fun validateAdoption(): WorkspaceAdoptionResult =
        when (val result = gateway.inspectSelection()) {
            is FsResult.Success -> WorkspaceAdoptionValidator.validate(result.value)
            is FsResult.Failure -> WorkspaceAdoptionResult.Rejected(
                WorkspaceAdoptionFailure.STORAGE_UNAVAILABLE,
            )
        }

    private fun WorkspaceSetupMode.toContract(): WorkspaceRootMode = when (this) {
        WorkspaceSetupMode.NEW -> WorkspaceRootMode.NEW
        WorkspaceSetupMode.ADOPT_EXISTING -> WorkspaceRootMode.ADOPT_EXISTING
    }

    private fun com.galleryorganizer.contract.WorkspaceRootSelection.toDomain() = WorkspaceRoot(
        displayName = displayName,
        relativePath = relativePath,
        mode = if (mode == WorkspaceRootMode.NEW) {
            WorkspaceSetupMode.NEW
        } else {
            WorkspaceSetupMode.ADOPT_EXISTING
        },
    )
}
