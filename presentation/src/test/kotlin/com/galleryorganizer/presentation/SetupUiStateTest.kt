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
        assertIs<SetupNotice.DeviceGateDeferred>(result.notice)
        assertFalse(result.canFinish)
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
}
