package com.galleryorganizer.presentation

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
}
enum class SetupMode { NEW, ADOPT }
enum class RootSelection { NONE, NEW_REQUESTED, EXISTING_REQUESTED }
enum class RootCapability { DEVICE_GATE_PENDING }
enum class RootAdoptionState { NOT_REQUESTED, NOT_APPLICABLE, DEVICE_GATE_PENDING }
enum class NotificationPermissionState { NOT_REQUESTED }
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

sealed interface SetupNotice {
    data object DeviceGateDeferred : SetupNotice
}

data class SetupUiState(
    val destination: SetupDestination,
    val workspaceEntered: Boolean,
    val mode: SetupMode,
    val scopeOptions: List<ScanScopeOption>,
    val selectedScopeIds: Set<String>,
    val protectedDirectoryIds: Set<String>,
    val trashRetention: TrashRetention,
    val notificationPreference: NotificationPreference,
    val notificationPermission: NotificationPermissionState,
    val rootSelection: RootSelection,
    val rootCapability: RootCapability,
    val rootAdoptionState: RootAdoptionState,
    val notice: SetupNotice?,
) {
    val canFinish: Boolean = false

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
            notificationPreference = NotificationPreference.OFF,
            notificationPermission = NotificationPermissionState.NOT_REQUESTED,
            rootSelection = RootSelection.NONE,
            rootCapability = RootCapability.DEVICE_GATE_PENDING,
            rootAdoptionState = RootAdoptionState.NOT_REQUESTED,
            notice = null,
        )
    }
}

sealed interface SetupEvent {
    data class ToggleScope(val scopeId: String) : SetupEvent
    data class ToggleProtectedDirectory(val directoryId: String) : SetupEvent
    data class SetTrashRetention(val retention: TrashRetention) : SetupEvent
    data class SetNotificationPreference(val preference: NotificationPreference) : SetupEvent
    data object ChooseNewRoot : SetupEvent
    data object ChooseExistingRoot : SetupEvent
    data object ProbeRoot : SetupEvent
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
    data object BackToSettings : SetupEvent
}

object SetupReducer {
    private val protectedDirectoryIds = setOf("archive", "screenshots", "downloads")

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
        is SetupEvent.SetNotificationPreference -> state.copy(
            notificationPreference = event.preference,
            notice = null,
        )
        SetupEvent.ChooseNewRoot -> state.blockedRootAction(
            mode = SetupMode.NEW,
            selection = RootSelection.NEW_REQUESTED,
            adoptionState = RootAdoptionState.NOT_APPLICABLE,
        )
        SetupEvent.ChooseExistingRoot -> state.blockedRootAction(
            mode = SetupMode.ADOPT,
            selection = RootSelection.EXISTING_REQUESTED,
            adoptionState = RootAdoptionState.DEVICE_GATE_PENDING,
        )
        SetupEvent.ProbeRoot -> state.copy(notice = SetupNotice.DeviceGateDeferred)
        SetupEvent.OpenSetupReview -> state.copy(
            destination = SetupDestination.SETUP_REVIEW,
            workspaceEntered = false,
            notice = SetupNotice.DeviceGateDeferred,
        )
        SetupEvent.Finish -> state.copy(
            destination = SetupDestination.LIBRARY,
            workspaceEntered = true,
            notice = SetupNotice.DeviceGateDeferred,
        )
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
        rootCapability = RootCapability.DEVICE_GATE_PENDING,
        rootAdoptionState = adoptionState,
        notice = SetupNotice.DeviceGateDeferred,
    )

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}
