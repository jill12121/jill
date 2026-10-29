package com.galleryorganizer.presentation

enum class SetupDestination { ONBOARDING, SETTINGS }
enum class SetupMode { NEW, ADOPT }
enum class RootSelection { NONE, NEW_REQUESTED, EXISTING_REQUESTED }
enum class RootCapability { DEVICE_GATE_PENDING }

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
    val mode: SetupMode,
    val scopeOptions: List<ScanScopeOption>,
    val selectedScopeIds: Set<String>,
    val rootSelection: RootSelection,
    val rootCapability: RootCapability,
    val notice: SetupNotice?,
) {
    val canFinish: Boolean = false

    companion object {
        fun initial(): SetupUiState = SetupUiState(
            destination = SetupDestination.ONBOARDING,
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
            rootSelection = RootSelection.NONE,
            rootCapability = RootCapability.DEVICE_GATE_PENDING,
            notice = null,
        )
    }
}

sealed interface SetupEvent {
    data class ToggleScope(val scopeId: String) : SetupEvent
    data object ChooseNewRoot : SetupEvent
    data object ChooseExistingRoot : SetupEvent
    data object ProbeRoot : SetupEvent
    data object Finish : SetupEvent
    data object OpenSetup : SetupEvent
    data object OpenSettings : SetupEvent
}

object SetupReducer {
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
        SetupEvent.ChooseNewRoot -> state.blockedRootAction(
            mode = SetupMode.NEW,
            selection = RootSelection.NEW_REQUESTED,
        )
        SetupEvent.ChooseExistingRoot -> state.blockedRootAction(
            mode = SetupMode.ADOPT,
            selection = RootSelection.EXISTING_REQUESTED,
        )
        SetupEvent.ProbeRoot,
        SetupEvent.Finish,
        -> state.copy(notice = SetupNotice.DeviceGateDeferred)
        SetupEvent.OpenSetup -> state.copy(destination = SetupDestination.ONBOARDING, notice = null)
        SetupEvent.OpenSettings -> state.copy(destination = SetupDestination.SETTINGS, notice = null)
    }

    private fun SetupUiState.blockedRootAction(
        mode: SetupMode,
        selection: RootSelection,
    ): SetupUiState = copy(
        mode = mode,
        rootSelection = selection,
        rootCapability = RootCapability.DEVICE_GATE_PENDING,
        notice = SetupNotice.DeviceGateDeferred,
    )

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}
