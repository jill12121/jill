package com.galleryorganizer.presentation

import com.galleryorganizer.domain.ImportStrategy
import com.galleryorganizer.domain.ScanCoverage

enum class SetupDestination {
    ONBOARDING,
    SETUP_REVIEW,
    SETTINGS,
    SCAN_SCOPES,
    ARCHIVE_LOCATION,
    PROTECTED_DIRECTORIES,
    TRASH_RETENTION,
    STORAGE_STATUS,
    LIBRARY,
    INBOX,
    TRASH,
    RECOVERY,
    NOTIFICATIONS,
    IMPORT_STRATEGY,
}
enum class SetupMode { NEW, ADOPT }
enum class RootSelection { NONE, NEW_REQUESTED, EXISTING_REQUESTED }
enum class RootCapability { UNSELECTED, SELECTING, READY, FAILED }
enum class RootAdoptionState {
    NOT_REQUESTED, NOT_APPLICABLE, SELECTING, ACCESS_GRANTED, VALIDATING, VERIFIED, FAILED,
}
enum class NotificationPermissionState { NOT_REQUESTED }
enum class MediaAccessState { NOT_REQUESTED, REQUESTING, FULL, PARTIAL, DENIED }
enum class SourceScanUiState { IDLE, SCANNING, READY, FAILED }
enum class NotificationPreference(val label: String) {
    OFF("Off"),
    OPERATION_STATUS("Operation status"),
    STATUS_AND_CLEANUP("Status and cleanup"),
}
enum class TrashRetention(val label: String) {
    SEVEN_DAYS("7 days"),
    THIRTY_DAYS("30 days"),
    NINETY_DAYS("90 days"),
}

data class ScanScopeOption(
    val id: String,
    val label: String,
    val detail: String,
)

data class DiscoveredBatchOption(
    val stableKey: String,
    val label: String,
    val imageCount: Int,
    val rootLooseImages: Boolean,
)

sealed interface SetupNotice {
    data object SelectionCancelled : SetupNotice
    data object RootRejected : SetupNotice
    data object RootRequired : SetupNotice
}

data class SetupUiState(
    val destination: SetupDestination,
    val workspaceEntered: Boolean,
    val mode: SetupMode,
    val scopeOptions: List<ScanScopeOption>,
    val selectedScopeIds: Set<String>,
    val protectedDirectoryIds: Set<String>,
    val trashRetention: TrashRetention,
    val importStrategy: ImportStrategy,
    val notificationPreference: NotificationPreference,
    val notificationPermission: NotificationPermissionState,
    val mediaAccess: MediaAccessState,
    val sourceScanState: SourceScanUiState,
    val discoveredBatches: List<DiscoveredBatchOption>,
    val selectedBatchKeys: Set<String>,
    val rootSelection: RootSelection,
    val rootCapability: RootCapability,
    val rootAdoptionState: RootAdoptionState,
    val selectedRootLabel: String?,
    val selectedRootRelativePath: String?,
    val adoptionAlbumCount: Int?,
    val adoptionImportingCount: Int?,
    val notice: SetupNotice?,
) {
    val canFinish: Boolean =
        rootCapability == RootCapability.READY &&
            selectedScopeIds.isNotEmpty() &&
            (mode == SetupMode.NEW || rootAdoptionState == RootAdoptionState.VERIFIED)

    companion object {
        fun initial(): SetupUiState = SetupUiState(
            destination = SetupDestination.ONBOARDING,
            workspaceEntered = false,
            mode = SetupMode.NEW,
            scopeOptions = listOf(
                ScanScopeOption("dcim", "Camera", "Photos captured on this device"),
                ScanScopeOption("pictures", "Pictures", "Screenshots and saved images"),
                ScanScopeOption("downloads", "Downloads", "Images downloaded from other apps"),
                ScanScopeOption(
                    "organizer-root",
                    "Organizer root",
                    "Off by default to prevent scanning the archive back into itself",
                ),
            ),
            selectedScopeIds = setOf("dcim", "pictures"),
            protectedDirectoryIds = setOf("archive"),
            trashRetention = TrashRetention.THIRTY_DAYS,
            importStrategy = ImportStrategy.COPY_KEEP_SOURCE,
            notificationPreference = NotificationPreference.OFF,
            notificationPermission = NotificationPermissionState.NOT_REQUESTED,
            mediaAccess = MediaAccessState.NOT_REQUESTED,
            sourceScanState = SourceScanUiState.IDLE,
            discoveredBatches = emptyList(),
            selectedBatchKeys = emptySet(),
            rootSelection = RootSelection.NONE,
            rootCapability = RootCapability.UNSELECTED,
            rootAdoptionState = RootAdoptionState.NOT_REQUESTED,
            selectedRootLabel = null,
            selectedRootRelativePath = null,
            adoptionAlbumCount = null,
            adoptionImportingCount = null,
            notice = null,
        )
    }
}

sealed interface SetupEvent {
    data class ToggleScope(val scopeId: String) : SetupEvent
    data class ToggleProtectedDirectory(val directoryId: String) : SetupEvent
    data class SetTrashRetention(val retention: TrashRetention) : SetupEvent
    data class SetImportStrategy(val strategy: ImportStrategy) : SetupEvent
    data class SetNotificationPreference(val preference: NotificationPreference) : SetupEvent
    data object ChooseNewRoot : SetupEvent
    data object ChooseExistingRoot : SetupEvent
    data class RootSelectionAccepted(
        val displayName: String,
        val mode: SetupMode,
        val relativePath: String? = null,
    ) : SetupEvent
    data object RootSelectionCancelled : SetupEvent
    data object RootSelectionFailed : SetupEvent
    data object AdoptionValidationStarted : SetupEvent
    data class AdoptionValidationSucceeded(
        val albumCount: Int,
        val importingCount: Int,
    ) : SetupEvent
    data object AdoptionValidationFailed : SetupEvent
    data object RequestMediaAccess : SetupEvent
    data class MediaAccessResolved(val coverage: ScanCoverage) : SetupEvent
    data object SourceScanStarted : SetupEvent
    data class SourceScanSucceeded(val batches: List<DiscoveredBatchOption>) : SetupEvent
    data object SourceScanFailed : SetupEvent
    data class ToggleDiscoveredBatch(val stableKey: String) : SetupEvent
    data object OpenSetupReview : SetupEvent
    data object Finish : SetupEvent
    data object OpenSetup : SetupEvent
    data object OpenSettings : SetupEvent
    data object OpenScanScopes : SetupEvent
    data object OpenArchiveLocation : SetupEvent
    data object OpenProtectedDirectories : SetupEvent
    data object OpenTrashRetention : SetupEvent
    data object OpenStorageStatus : SetupEvent
    data object OpenLibrary : SetupEvent
    data object OpenInbox : SetupEvent
    data object OpenTrash : SetupEvent
    data object OpenRecovery : SetupEvent
    data object OpenNotifications : SetupEvent
    data object OpenImportStrategy : SetupEvent
    data object BackToSettings : SetupEvent
}

object SetupReducer {
    private val protectedDirectoryIds = setOf("archive", "screenshots", "downloads")
    private val supportedImportStrategies = setOf(
        ImportStrategy.COPY_KEEP_SOURCE,
        ImportStrategy.MOVE_SOURCE,
    )

    fun reduce(state: SetupUiState, event: SetupEvent): SetupUiState = when (event) {
        is SetupEvent.ToggleScope -> {
            if (state.scopeOptions.none { it.id == event.scopeId }) {
                state
            } else {
                state.copy(
                    selectedScopeIds = state.selectedScopeIds.toggle(event.scopeId),
                    notice = null,
                )
            }
        }
        is SetupEvent.ToggleProtectedDirectory -> {
            if (event.directoryId !in protectedDirectoryIds) {
                state
            } else {
                state.copy(
                    protectedDirectoryIds =
                    state.protectedDirectoryIds.toggle(event.directoryId),
                    notice = null,
                )
            }
        }
        is SetupEvent.SetTrashRetention -> state.copy(
            trashRetention = event.retention,
            notice = null,
        )
        is SetupEvent.SetImportStrategy -> {
            if (event.strategy !in supportedImportStrategies) {
                state
            } else {
                state.copy(importStrategy = event.strategy, notice = null)
            }
        }
        is SetupEvent.SetNotificationPreference -> state.copy(
            notificationPreference = event.preference,
            notice = null,
        )
        SetupEvent.RequestMediaAccess -> state.copy(
            mediaAccess = MediaAccessState.REQUESTING,
            sourceScanState = SourceScanUiState.IDLE,
            discoveredBatches = emptyList(),
            selectedBatchKeys = emptySet(),
        )
        is SetupEvent.MediaAccessResolved -> state.copy(
            mediaAccess = when (event.coverage) {
                ScanCoverage.FULL -> MediaAccessState.FULL
                ScanCoverage.PARTIAL_USER_SELECTED -> MediaAccessState.PARTIAL
                ScanCoverage.UNKNOWN -> MediaAccessState.DENIED
            },
            sourceScanState = SourceScanUiState.IDLE,
            discoveredBatches = emptyList(),
            selectedBatchKeys = emptySet(),
        )
        SetupEvent.SourceScanStarted ->
            if (state.mediaAccess == MediaAccessState.FULL ||
                state.mediaAccess == MediaAccessState.PARTIAL
            ) {
                state.copy(sourceScanState = SourceScanUiState.SCANNING)
            } else state
        is SetupEvent.SourceScanSucceeded ->
            if (state.sourceScanState == SourceScanUiState.SCANNING) {
                state.copy(
                    sourceScanState = SourceScanUiState.READY,
                    discoveredBatches = event.batches,
                    selectedBatchKeys = emptySet(),
                )
            } else state
        SetupEvent.SourceScanFailed ->
            if (state.sourceScanState == SourceScanUiState.SCANNING) {
                state.copy(sourceScanState = SourceScanUiState.FAILED)
            } else state
        is SetupEvent.ToggleDiscoveredBatch ->
            if (state.sourceScanState == SourceScanUiState.READY &&
                state.discoveredBatches.any { it.stableKey == event.stableKey }
            ) {
                state.copy(selectedBatchKeys = state.selectedBatchKeys.toggle(event.stableKey))
            } else state
        SetupEvent.ChooseNewRoot -> state.blockedRootAction(
            mode = SetupMode.NEW,
            selection = RootSelection.NEW_REQUESTED,
            adoptionState = RootAdoptionState.NOT_APPLICABLE,
        )
        SetupEvent.ChooseExistingRoot -> state.blockedRootAction(
            mode = SetupMode.ADOPT,
            selection = RootSelection.EXISTING_REQUESTED,
            adoptionState = RootAdoptionState.SELECTING,
        )
        is SetupEvent.RootSelectionAccepted -> state.copy(
            mode = event.mode,
            rootSelection = if (event.mode == SetupMode.ADOPT) {
                RootSelection.EXISTING_REQUESTED
            } else {
                RootSelection.NEW_REQUESTED
            },
            rootCapability = RootCapability.READY,
            rootAdoptionState = if (event.mode == SetupMode.ADOPT) {
                RootAdoptionState.ACCESS_GRANTED
            } else {
                RootAdoptionState.NOT_APPLICABLE
            },
            selectedRootLabel = event.displayName,
            selectedRootRelativePath = event.relativePath,
            sourceScanState = SourceScanUiState.IDLE,
            discoveredBatches = emptyList(),
            selectedBatchKeys = emptySet(),
            adoptionAlbumCount = null,
            adoptionImportingCount = null,
            notice = null,
        )
        SetupEvent.RootSelectionCancelled -> state.copy(
            rootSelection = RootSelection.NONE,
            rootCapability = RootCapability.UNSELECTED,
            rootAdoptionState = RootAdoptionState.NOT_REQUESTED,
            selectedRootLabel = null,
            selectedRootRelativePath = null,
            adoptionAlbumCount = null,
            adoptionImportingCount = null,
            notice = SetupNotice.SelectionCancelled,
        )
        SetupEvent.RootSelectionFailed -> state.copy(
            rootCapability = RootCapability.FAILED,
            rootAdoptionState = if (state.mode == SetupMode.ADOPT) {
                RootAdoptionState.FAILED
            } else {
                RootAdoptionState.NOT_APPLICABLE
            },
            selectedRootLabel = null,
            selectedRootRelativePath = null,
            adoptionAlbumCount = null,
            adoptionImportingCount = null,
            notice = SetupNotice.RootRejected,
        )
        SetupEvent.AdoptionValidationStarted ->
            if (state.mode == SetupMode.ADOPT && state.rootCapability == RootCapability.READY) {
                state.copy(rootAdoptionState = RootAdoptionState.VALIDATING, notice = null)
            } else {
                state
            }
        is SetupEvent.AdoptionValidationSucceeded ->
            if (state.rootAdoptionState == RootAdoptionState.VALIDATING) {
                state.copy(
                    rootAdoptionState = RootAdoptionState.VERIFIED,
                    adoptionAlbumCount = event.albumCount,
                    adoptionImportingCount = event.importingCount,
                    notice = null,
                )
            } else {
                state
            }
        SetupEvent.AdoptionValidationFailed ->
            if (state.rootAdoptionState == RootAdoptionState.VALIDATING) {
                state.copy(
                    rootAdoptionState = RootAdoptionState.FAILED,
                    adoptionAlbumCount = null,
                    adoptionImportingCount = null,
                    notice = SetupNotice.RootRejected,
                )
            } else {
                state
            }
        SetupEvent.OpenSetupReview -> state.copy(
            destination = SetupDestination.SETUP_REVIEW,
            workspaceEntered = false,
        )
        SetupEvent.Finish -> if (state.canFinish) {
            state.copy(destination = SetupDestination.LIBRARY, workspaceEntered = true, notice = null)
        } else {
            state.copy(notice = SetupNotice.RootRequired)
        }
        SetupEvent.OpenSetup -> state.copy(
            destination = SetupDestination.ONBOARDING,
            workspaceEntered = false,
            notice = null,
        )
        SetupEvent.OpenSettings -> state.copy(destination = SetupDestination.SETTINGS, notice = null)
        SetupEvent.OpenScanScopes ->
            state.copy(destination = SetupDestination.SCAN_SCOPES, notice = null)
        SetupEvent.OpenArchiveLocation ->
            state.copy(destination = SetupDestination.ARCHIVE_LOCATION, notice = null)
        SetupEvent.OpenProtectedDirectories ->
            state.copy(destination = SetupDestination.PROTECTED_DIRECTORIES, notice = null)
        SetupEvent.OpenTrashRetention ->
            state.copy(destination = SetupDestination.TRASH_RETENTION, notice = null)
        SetupEvent.OpenStorageStatus ->
            state.copy(destination = SetupDestination.STORAGE_STATUS, notice = null)
        SetupEvent.OpenLibrary ->
            state.copy(
                destination = SetupDestination.LIBRARY,
                workspaceEntered = true,
                notice = null,
            )
        SetupEvent.OpenInbox ->
            state.copy(
                destination = SetupDestination.INBOX,
                workspaceEntered = true,
                notice = null,
            )
        SetupEvent.OpenTrash ->
            state.copy(
                destination = SetupDestination.TRASH,
                workspaceEntered = true,
                notice = null,
            )
        SetupEvent.OpenRecovery ->
            state.copy(
                destination = SetupDestination.RECOVERY,
                workspaceEntered = true,
                notice = null,
            )
        SetupEvent.OpenNotifications ->
            state.copy(destination = SetupDestination.NOTIFICATIONS, notice = null)
        SetupEvent.OpenImportStrategy ->
            state.copy(destination = SetupDestination.IMPORT_STRATEGY, notice = null)
        SetupEvent.BackToSettings ->
            state.copy(destination = SetupDestination.SETTINGS, notice = null)
    }

    private fun SetupUiState.blockedRootAction(
        mode: SetupMode,
        selection: RootSelection,
        adoptionState: RootAdoptionState,
    ): SetupUiState = copy(
        destination = SetupDestination.ARCHIVE_LOCATION,
        mode = mode,
        rootSelection = selection,
        rootCapability = RootCapability.SELECTING,
        rootAdoptionState = adoptionState,
        selectedRootLabel = null,
        selectedRootRelativePath = null,
        sourceScanState = SourceScanUiState.IDLE,
        discoveredBatches = emptyList(),
        selectedBatchKeys = emptySet(),
        adoptionAlbumCount = null,
        adoptionImportingCount = null,
        notice = null,
    )

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}
