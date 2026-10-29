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
    fun `root actions remain blocked while device evidence is deferred`() {
        val initial = SetupUiState.initial()

        val result = SetupReducer.reduce(initial, SetupEvent.ChooseExistingRoot)

        assertEquals(RootSelection.EXISTING_REQUESTED, result.rootSelection)
        assertEquals(RootCapability.DEVICE_GATE_PENDING, result.rootCapability)
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
}
