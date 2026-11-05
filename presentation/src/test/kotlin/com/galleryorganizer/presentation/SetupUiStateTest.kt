package com.galleryorganizer.presentation

import com.galleryorganizer.domain.ImportStrategy
import com.galleryorganizer.domain.ScanCoverage
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
        assertEquals(RootCapability.UNSELECTED, state.rootCapability)
        assertEquals(RootAdoptionState.NOT_REQUESTED, state.rootAdoptionState)
        assertEquals(NotificationPreference.OFF, state.notificationPreference)
        assertEquals(NotificationPermissionState.NOT_REQUESTED, state.notificationPermission)
        assertEquals(MediaAccessState.NOT_REQUESTED, state.mediaAccess)
        assertEquals(SourceScanUiState.IDLE, state.sourceScanState)
        assertEquals(emptyList(), state.discoveredBatches)
        assertEquals(ImportStrategy.COPY_KEEP_SOURCE, state.importStrategy)
        assertFalse(state.canFinish)
    }

    @Test
    fun `media permission and scan states fail closed`() {
        val requesting = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.RequestMediaAccess,
        )
        val denied = SetupReducer.reduce(
            requesting,
            SetupEvent.MediaAccessResolved(ScanCoverage.UNKNOWN),
        )

        assertEquals(MediaAccessState.REQUESTING, requesting.mediaAccess)
        assertEquals(MediaAccessState.DENIED, denied.mediaAccess)
        assertEquals(SourceScanUiState.IDLE, denied.sourceScanState)
        assertEquals(emptyList(), denied.discoveredBatches)
    }

    @Test
    fun `completed discovery exposes selectable first level batches`() {
        val scanning = SetupReducer.reduce(
            SetupReducer.reduce(
                SetupUiState.initial(),
                SetupEvent.MediaAccessResolved(ScanCoverage.FULL),
            ),
            SetupEvent.SourceScanStarted,
        )
        val ready = SetupReducer.reduce(
            scanning,
            SetupEvent.SourceScanSucceeded(
                listOf(
                    DiscoveredBatchOption("directory:trip", "Trip", 3, false),
                    DiscoveredBatchOption("virtual:loose", "Loose images", 1, true),
                ),
            ),
        )
        val selected = SetupReducer.reduce(
            ready,
            SetupEvent.ToggleDiscoveredBatch("directory:trip"),
        )

        assertEquals(MediaAccessState.FULL, ready.mediaAccess)
        assertEquals(SourceScanUiState.READY, ready.sourceScanState)
        assertEquals(2, ready.discoveredBatches.size)
        assertEquals(setOf("directory:trip"), selected.selectedBatchKeys)
    }

    @Test
    fun `new root invalidates stale discovered batches`() {
        val ready = SetupReducer.reduce(
            SetupReducer.reduce(
                SetupReducer.reduce(
                    SetupUiState.initial(),
                    SetupEvent.MediaAccessResolved(ScanCoverage.FULL),
                ),
                SetupEvent.SourceScanStarted,
            ),
            SetupEvent.SourceScanSucceeded(
                listOf(DiscoveredBatchOption("directory:trip", "Trip", 3, false)),
            ),
        )
        val selecting = SetupReducer.reduce(ready, SetupEvent.ChooseNewRoot)

        assertEquals(SourceScanUiState.IDLE, selecting.sourceScanState)
        assertEquals(emptyList(), selecting.discoveredBatches)
        assertEquals(emptySet(), selecting.selectedBatchKeys)
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
    fun `archive choice enters selecting state`() {
        val initial = SetupUiState.initial()

        val result = SetupReducer.reduce(initial, SetupEvent.ChooseExistingRoot)

        assertEquals(SetupMode.ADOPT, result.mode)
        assertEquals(RootSelection.EXISTING_REQUESTED, result.rootSelection)
        assertEquals(RootCapability.SELECTING, result.rootCapability)
        assertEquals(RootAdoptionState.SELECTING, result.rootAdoptionState)
        assertEquals(null, result.notice)
        assertFalse(result.canFinish)

        val accepted = SetupReducer.reduce(
            result,
            SetupEvent.RootSelectionAccepted("Existing archive", SetupMode.ADOPT),
        )
        assertEquals(RootAdoptionState.ACCESS_GRANTED, accepted.rootAdoptionState)
        assertFalse(accepted.canFinish)

        val validating = SetupReducer.reduce(accepted, SetupEvent.AdoptionValidationStarted)
        val verified = SetupReducer.reduce(
            validating,
            SetupEvent.AdoptionValidationSucceeded(albumCount = 3, importingCount = 1),
        )
        assertEquals(RootAdoptionState.VALIDATING, validating.rootAdoptionState)
        assertEquals(RootAdoptionState.VERIFIED, verified.rootAdoptionState)
        assertEquals(3, verified.adoptionAlbumCount)
        assertEquals(1, verified.adoptionImportingCount)
        assertTrue(verified.canFinish)

        val switched = SetupReducer.reduce(validating, SetupEvent.ChooseNewRoot)
        val stale = SetupReducer.reduce(
            switched,
            SetupEvent.AdoptionValidationSucceeded(9, 9),
        )
        assertEquals(switched, stale)
    }

    @Test
    fun `new root selection cannot retain an existing root adoption claim`() {
        val existing = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.ChooseExistingRoot,
        )
        val newRoot = SetupReducer.reduce(existing, SetupEvent.ChooseNewRoot)

        assertEquals(SetupMode.NEW, newRoot.mode)
        assertEquals(RootSelection.NEW_REQUESTED, newRoot.rootSelection)
        assertEquals(RootAdoptionState.NOT_APPLICABLE, newRoot.rootAdoptionState)
        assertEquals(RootCapability.SELECTING, newRoot.rootCapability)
    }

    @Test
    fun `accepted root enables finish while cancellation and failure remain blocked`() {
        val selecting = SetupReducer.reduce(SetupUiState.initial(), SetupEvent.ChooseNewRoot)
        val cancelled = SetupReducer.reduce(selecting, SetupEvent.RootSelectionCancelled)
        val failed = SetupReducer.reduce(selecting, SetupEvent.RootSelectionFailed)
        val ready = SetupReducer.reduce(
            selecting,
            SetupEvent.RootSelectionAccepted("GalleryOrganizer", SetupMode.NEW),
        )
        val result = SetupReducer.reduce(ready, SetupEvent.Finish)

        assertEquals(RootCapability.UNSELECTED, cancelled.rootCapability)
        assertIs<SetupNotice.SelectionCancelled>(cancelled.notice)
        assertEquals(RootCapability.FAILED, failed.rootCapability)
        assertIs<SetupNotice.RootRejected>(failed.notice)
        assertEquals("GalleryOrganizer", ready.selectedRootLabel)
        assertTrue(ready.canFinish)
        assertEquals(SetupDestination.LIBRARY, result.destination)
        assertTrue(result.workspaceEntered)
        assertTrue(result.canFinish)
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
        assertEquals(RootAdoptionState.SELECTING, reviewed.rootAdoptionState)
        assertEquals(RootCapability.SELECTING, reviewed.rootCapability)
        assertEquals(null, reviewed.notice)
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
        assertEquals(
            SetupDestination.IMPORT_STRATEGY,
            SetupReducer.reduce(initial, SetupEvent.OpenImportStrategy).destination,
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
        val finished = readyState()
        val inbox = SetupReducer.reduce(finished, SetupEvent.OpenInbox)
        val library = SetupReducer.reduce(inbox, SetupEvent.OpenLibrary)

        assertEquals(SetupDestination.INBOX, inbox.destination)
        assertTrue(inbox.workspaceEntered)
        assertEquals(SetupDestination.LIBRARY, library.destination)
        assertEquals(RootCapability.READY, library.rootCapability)
    }

    @Test
    fun `trash and recovery shells preserve workspace state without enabling capabilities`() {
        val finished = readyState()
        val trash = SetupReducer.reduce(finished, SetupEvent.OpenTrash)
        val recovery = SetupReducer.reduce(trash, SetupEvent.OpenRecovery)

        assertEquals(SetupDestination.TRASH, trash.destination)
        assertTrue(trash.workspaceEntered)
        assertEquals(SetupDestination.RECOVERY, recovery.destination)
        assertTrue(recovery.workspaceEntered)
        assertEquals(RootCapability.READY, recovery.rootCapability)
        assertTrue(recovery.canFinish)
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

    @Test
    fun `supported import strategy is editable in memory without creating a plan`() {
        val detail = SetupReducer.reduce(
            SetupUiState.initial(),
            SetupEvent.OpenImportStrategy,
        )
        val edited = SetupReducer.reduce(
            detail,
            SetupEvent.SetImportStrategy(ImportStrategy.MOVE_SOURCE),
        )
        val settings = SetupReducer.reduce(edited, SetupEvent.BackToSettings)

        assertEquals(SetupDestination.IMPORT_STRATEGY, detail.destination)
        assertEquals(ImportStrategy.MOVE_SOURCE, settings.importStrategy)
        assertEquals(RootCapability.UNSELECTED, settings.rootCapability)
        assertEquals(SetupDestination.SETTINGS, settings.destination)
    }

    @Test
    fun `index in place remains hidden and cannot replace the safe import strategy`() {
        val initial = SetupUiState.initial()
        val result = SetupReducer.reduce(
            initial,
            SetupEvent.SetImportStrategy(ImportStrategy.INDEX_IN_PLACE),
        )

        assertEquals(initial, result)
        assertEquals(ImportStrategy.COPY_KEEP_SOURCE, result.importStrategy)
    }

    private fun readyState(): SetupUiState {
        val selecting = SetupReducer.reduce(SetupUiState.initial(), SetupEvent.ChooseNewRoot)
        val ready = SetupReducer.reduce(
            selecting,
            SetupEvent.RootSelectionAccepted("GalleryOrganizer", SetupMode.NEW),
        )
        return SetupReducer.reduce(ready, SetupEvent.Finish)
    }
}
