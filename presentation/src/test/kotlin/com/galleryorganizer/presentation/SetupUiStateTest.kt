package com.galleryorganizer.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SetupUiStateTest {
    @Test
    fun `initial setup selects safe media scopes but never organizer root`() {
        val state = SetupUiState.initial()

        assertEquals(setOf("dcim", "pictures"), state.selectedScopeIds)
        assertFalse("organizer-root" in state.selectedScopeIds)
        assertEquals(RootCapability.DEVICE_GATE_PENDING, state.rootCapability)
        assertEquals(RootAdoptionState.NOT_REQUESTED, state.rootAdoptionState)
        assertEquals(NotificationPreference.OFF, state.notificationPreference)
        assertEquals(NotificationPermissionState.NOT_REQUESTED, state.notificationPermission)
        assertFalse(state.canFinish)
    }

    @Test
    fun `scope toggles are in memory and organizer root remains opt in`() {
        val initial = SetupUiState.initial()

        val downloads = SetupReducer.reduce(initial, SetupEvent.ToggleScope("downloads"))
        val root = SetupReducer.reduce(downloads, SetupEvent.ToggleScope("organizer-root"))

        assertTrue("downloads" in downloads.selectedScopeIds)
        assertFalse("organizer-root" in downloads.selectedScopeIds)
        assertTrue("organizer-root" in root.selectedScopeIds)
    }

    @Test
    fun `archive choices update the in memory draft while device evidence is deferred`() {
        val initial = SetupUiState.initial()

        val result = SetupReducer.reduce(initial, SetupEvent.ChooseExistingRoot)

        assertEquals(SetupMode.ADOPT, result.mode)
        assertEquals(RootSelection.EXISTING_REQUESTED, result.rootSelection)
        assertEquals(RootCapability.DEVICE_GATE_PENDING, result.rootCapability)
        assertEquals(RootAdoptionState.DEVICE_GATE_PENDING, result.rootAdoptionState)
        assertIs<SetupNotice.DeviceGateDeferred>(result.notice)
        assertFalse(result.canFinish)
    }

    @Test
    fun `new root selection cannot retain an existing root adoption claim`() {
        val existing = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.ChooseExistingRoot,
        )
        val newRoot = SetupReducer.reduce(existing, SetupEvent.ChooseNewRoot)
        val probed = SetupReducer.reduce(newRoot, SetupEvent.ProbeRoot)

        assertEquals(SetupMode.NEW, newRoot.mode)
        assertEquals(RootSelection.NEW_REQUESTED, newRoot.rootSelection)
        assertEquals(RootAdoptionState.NOT_APPLICABLE, newRoot.rootAdoptionState)
        assertEquals(RootAdoptionState.NOT_APPLICABLE, probed.rootAdoptionState)
        assertEquals(RootCapability.DEVICE_GATE_PENDING, probed.rootCapability)
        assertIs<SetupNotice.DeviceGateDeferred>(probed.notice)
    }

    @Test
    fun `finish opens the library shell without enabling storage capabilities`() {
        val result = SetupReducer.reduce(SetupUiState.initial(), SetupEvent.Finish)

        assertEquals(SetupDestination.LIBRARY, result.destination)
        assertTrue(result.workspaceEntered)
        assertIs<SetupNotice.DeviceGateDeferred>(result.notice)
        assertFalse(result.canFinish)
    }

    @Test
    fun `setup review preserves the draft without entering the workspace`() {
        val scoped = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.ToggleScope("downloads"),
        )
        val adopting = SetupReducer.reduce(scoped, SetupEvent.ChooseExistingRoot)
        val reviewed = SetupReducer.reduce(adopting, SetupEvent.OpenSetupReview)

        assertEquals(SetupDestination.SETUP_REVIEW, reviewed.destination)
        assertFalse(reviewed.workspaceEntered)
        assertEquals(scoped.selectedScopeIds, reviewed.selectedScopeIds)
        assertEquals(RootSelection.EXISTING_REQUESTED, reviewed.rootSelection)
        assertEquals(RootAdoptionState.DEVICE_GATE_PENDING, reviewed.rootAdoptionState)
        assertEquals(RootCapability.DEVICE_GATE_PENDING, reviewed.rootCapability)
        assertIs<SetupNotice.DeviceGateDeferred>(reviewed.notice)
        assertFalse(reviewed.canFinish)
    }

    @Test
    fun `settings navigation preserves setup choices`() {
        val selected = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.ToggleScope("downloads"),
        )

        val settings = SetupReducer.reduce(selected, SetupEvent.OpenSettings)
        val restored = SetupReducer.reduce(settings, SetupEvent.OpenSetup)

        assertEquals(SetupDestination.SETTINGS, settings.destination)
        assertEquals(SetupDestination.ONBOARDING, restored.destination)
        assertEquals(selected.selectedScopeIds, restored.selectedScopeIds)
    }

    @Test
    fun `every settings row opens its dedicated destination`() {
        val initial = SetupUiState.initial()

        assertEquals(
            SetupDestination.SCAN_SCOPES,
            SetupReducer.reduce(initial, SetupEvent.OpenScanScopes).destination,
        )
        assertEquals(
            SetupDestination.ARCHIVE_LOCATION,
            SetupReducer.reduce(initial, SetupEvent.OpenArchiveLocation).destination,
        )
        assertEquals(
            SetupDestination.PROTECTED_DIRECTORIES,
            SetupReducer.reduce(initial, SetupEvent.OpenProtectedDirectories).destination,
        )
        assertEquals(
            SetupDestination.TRASH_RETENTION,
            SetupReducer.reduce(initial, SetupEvent.OpenTrashRetention).destination,
        )
        assertEquals(
            SetupDestination.STORAGE_STATUS,
            SetupReducer.reduce(initial, SetupEvent.OpenStorageStatus).destination,
        )
        assertEquals(
            SetupDestination.TRASH,
            SetupReducer.reduce(initial, SetupEvent.OpenTrash).destination,
        )
        assertEquals(
            SetupDestination.RECOVERY,
            SetupReducer.reduce(initial, SetupEvent.OpenRecovery).destination,
        )
        assertEquals(
            SetupDestination.NOTIFICATIONS,
            SetupReducer.reduce(initial, SetupEvent.OpenNotifications).destination,
        )
    }

    @Test
    fun `protected directories and retention are editable in memory`() {
        val initial = SetupUiState.initial()

        val protected = SetupReducer.reduce(
            initial,
            SetupEvent.ToggleProtectedDirectory("screenshots"),
        )
        val retained = SetupReducer.reduce(
            protected,
            SetupEvent.SetTrashRetention(TrashRetention.NINETY_DAYS),
        )

        assertTrue("screenshots" in protected.protectedDirectoryIds)
        assertEquals(TrashRetention.NINETY_DAYS, retained.trashRetention)
    }

    @Test
    fun `back from a settings detail returns to settings and preserves edits`() {
        val edited = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.SetTrashRetention(TrashRetention.SEVEN_DAYS),
        )
        val detail = SetupReducer.reduce(edited, SetupEvent.OpenTrashRetention)
        val settings = SetupReducer.reduce(detail, SetupEvent.BackToSettings)

        assertEquals(SetupDestination.SETTINGS, settings.destination)
        assertEquals(TrashRetention.SEVEN_DAYS, settings.trashRetention)
    }

    @Test
    fun `workspace navigation opens inbox and returns to library`() {
        val finished = SetupReducer.reduce(SetupUiState.initial(), SetupEvent.Finish)
        val inbox = SetupReducer.reduce(finished, SetupEvent.OpenInbox)
        val library = SetupReducer.reduce(inbox, SetupEvent.OpenLibrary)

        assertEquals(SetupDestination.INBOX, inbox.destination)
        assertTrue(inbox.workspaceEntered)
        assertEquals(SetupDestination.LIBRARY, library.destination)
        assertEquals(RootCapability.DEVICE_GATE_PENDING, library.rootCapability)
    }

    @Test
    fun `trash and recovery shells preserve workspace state without enabling capabilities`() {
        val finished = SetupReducer.reduce(SetupUiState.initial(), SetupEvent.Finish)
        val trash = SetupReducer.reduce(finished, SetupEvent.OpenTrash)
        val recovery = SetupReducer.reduce(trash, SetupEvent.OpenRecovery)

        assertEquals(SetupDestination.TRASH, trash.destination)
        assertTrue(trash.workspaceEntered)
        assertEquals(SetupDestination.RECOVERY, recovery.destination)
        assertTrue(recovery.workspaceEntered)
        assertEquals(RootCapability.DEVICE_GATE_PENDING, recovery.rootCapability)
        assertFalse(recovery.canFinish)
    }

    @Test
    fun `notification preference is editable in memory without requesting permission`() {
        val detail = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.OpenNotifications,
        )
        val edited = SetupReducer.reduce(
            detail,
            SetupEvent.SetNotificationPreference(NotificationPreference.STATUS_AND_CLEANUP),
        )
        val settings = SetupReducer.reduce(edited, SetupEvent.BackToSettings)

        assertEquals(SetupDestination.NOTIFICATIONS, detail.destination)
        assertEquals(NotificationPreference.STATUS_AND_CLEANUP, settings.notificationPreference)
        assertEquals(NotificationPermissionState.NOT_REQUESTED, settings.notificationPermission)
        assertEquals(SetupDestination.SETTINGS, settings.destination)
    }
}
