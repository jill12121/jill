package com.galleryorganizer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galleryorganizer.domain.ImportStrategy

private val AppBackground = Color(0xFFF6F7F4)
private val StatusAmber = Color(0xFF8A5A00)
private val StatusAmberSurface = Color(0xFFFFE8B2)

@Composable
fun SetupScreen(
    state: SetupUiState,
    libraryState: LibraryUiState,
    inboxState: InboxUiState,
    imageViewerState: ImageViewerUiState,
    onEvent: (SetupEvent) -> Unit,
    onLibraryEvent: (LibraryEvent) -> Unit,
    onInboxEvent: (InboxEvent) -> Unit,
    onOpenImageViewer: (String) -> Unit,
    onImageViewerEvent: (ImageViewerEvent) -> Unit,
    inboxThumbnails: Map<String, ImageBitmap> = emptyMap(),
    imageViewerPreview: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    if (imageViewerState.isOpen) {
        ImageViewerScreen(
            state = imageViewerState,
            preview = imageViewerPreview,
            onEvent = onImageViewerEvent,
            modifier = modifier,
        )
        return
    }
    Surface(modifier = modifier.fillMaxSize(), color = AppBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            AppHeader(state.destination, state.workspaceEntered, onEvent)
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (state.destination) {
                    SetupDestination.ONBOARDING -> OnboardingContent(state, onEvent)
                    SetupDestination.SETUP_REVIEW -> SetupReviewContent(state, onEvent)
                    SetupDestination.SETTINGS -> SettingsContent(state, onEvent)
                    SetupDestination.SCAN_SCOPES -> ScanScopesContent(state, onEvent)
                    SetupDestination.ARCHIVE_LOCATION -> ArchiveLocationContent(state, onEvent)
                    SetupDestination.PROTECTED_DIRECTORIES ->
                        ProtectedDirectoriesContent(state, onEvent)
                    SetupDestination.TRASH_RETENTION -> TrashRetentionContent(state, onEvent)
                    SetupDestination.STORAGE_STATUS -> StorageStatusContent(state, onEvent)
                    SetupDestination.LIBRARY -> AlbumLibraryContent(
                        setupState = state,
                        libraryState = libraryState,
                        inboxState = inboxState,
                        onLibraryEvent = onLibraryEvent,
                        onSetupEvent = onEvent,
                    )
                    SetupDestination.INBOX -> InboxContent(
                        setupState = state,
                        inboxState = inboxState,
                        libraryState = libraryState,
                        onSetupEvent = onEvent,
                        onInboxEvent = onInboxEvent,
                        onOpenImageViewer = onOpenImageViewer,
                        thumbnails = inboxThumbnails,
                    )
                    SetupDestination.TRASH -> TrashContent(onEvent)
                    SetupDestination.RECOVERY -> RecoveryContent(onEvent)
                    SetupDestination.NOTIFICATIONS -> NotificationsContent(state, onEvent)
                    SetupDestination.IMPORT_STRATEGY -> ImportStrategyContent(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    destination: SetupDestination,
    workspaceEntered: Boolean,
    onEvent: (SetupEvent) -> Unit,
) {
    Text(
        text = "Gallery Organizer",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Local-only photo organization",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (workspaceEntered) {
            NavButton("Library", destination == SetupDestination.LIBRARY) {
                onEvent(SetupEvent.OpenLibrary)
            }
            NavButton("Inbox", destination == SetupDestination.INBOX) {
                onEvent(SetupEvent.OpenInbox)
            }
            NavButton("Settings", destination.isSettingsDestination()) {
                onEvent(SetupEvent.OpenSettings)
            }
        } else {
            NavButton(
                "Setup",
                destination == SetupDestination.ONBOARDING ||
                    destination == SetupDestination.SETUP_REVIEW,
            ) {
                onEvent(SetupEvent.OpenSetup)
            }
            NavButton("Library", destination == SetupDestination.LIBRARY) {
                onEvent(SetupEvent.OpenLibrary)
            }
            NavButton("Settings", destination.isSettingsDestination()) {
                onEvent(SetupEvent.OpenSettings)
            }
        }
    }
}

@Composable
private fun NavButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun SetupDestination.isSettingsDestination(): Boolean = when (this) {
    SetupDestination.SETTINGS,
    SetupDestination.SCAN_SCOPES,
    SetupDestination.ARCHIVE_LOCATION,
    SetupDestination.PROTECTED_DIRECTORIES,
    SetupDestination.TRASH_RETENTION,
    SetupDestination.STORAGE_STATUS,
    SetupDestination.TRASH,
    SetupDestination.RECOVERY,
    SetupDestination.NOTIFICATIONS,
    SetupDestination.IMPORT_STRATEGY,
    -> true
    SetupDestination.ONBOARDING,
    SetupDestination.SETUP_REVIEW,
    SetupDestination.LIBRARY,
    SetupDestination.INBOX,
    -> false
}

@Composable
private fun OnboardingContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        PageTitle(
            title = "Choose what to scan",
            detail = "Selections stay on this device. The organizer root is never scanned automatically.",
        )
        state.scopeOptions.forEach { option ->
            ToggleRow(
                label = option.label,
                detail = option.detail,
                checked = option.id in state.selectedScopeIds,
                onClick = { onEvent(SetupEvent.ToggleScope(option.id)) },
            )
        }
        MediaDiscoverySection(state, onEvent)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Archive location",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Choose a folder that Gallery Organizer can access.",
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onEvent(SetupEvent.ChooseNewRoot) }) {
                Text("Choose new")
            }
            OutlinedButton(onClick = { onEvent(SetupEvent.ChooseExistingRoot) }) {
                Text("Use existing")
            }
        }
        RootStatusNotice(state)
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = { onEvent(SetupEvent.OpenSetupReview) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Review setup")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SetupReviewContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) {
    val selectedScopeLabels = state.scopeOptions
        .filter { it.id in state.selectedScopeIds }
        .joinToString { it.label }
        .ifEmpty { "None" }

    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        TextButton(onClick = { onEvent(SetupEvent.OpenSetup) }) {
            Text("< Setup")
        }
        PageTitle(
            title = "Review setup",
            detail = "Confirm the in-memory configuration before entering the workspace preview.",
        )
        StatusValueLine("Scan scopes", selectedScopeLabels)
        StatusValueLine("Archive mode", state.rootSelection.label())
        StatusValueLine("Adoption checks", state.rootAdoptionState.label())
        StatusValueLine(
            "Protected directories",
            "${state.protectedDirectoryIds.size} selected",
        )
        StatusValueLine("Trash retention", state.trashRetention.label)
        StatusValueLine("Import strategy", state.importStrategy.label())
        StatusValueLine("Notifications", state.notificationPreference.label)
        RootStatusNotice(state)
        Button(
            onClick = { onEvent(SetupEvent.Finish) },
            enabled = state.canFinish,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text("Enter workspace preview")
        }
    }
}

@Composable
private fun SettingsContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        PageTitle(
            title = "Settings",
            detail = "Setup preferences and archive access stay on this device.",
        )
        SettingLine(
            "Scan scopes",
            "${state.selectedScopeIds.size} selected",
        ) { onEvent(SetupEvent.OpenScanScopes) }
        SettingLine(
            "Archive location",
            state.rootSelection.label(),
        ) { onEvent(SetupEvent.OpenArchiveLocation) }
        SettingLine(
            "Protected directories",
            "${state.protectedDirectoryIds.size} selected",
        ) { onEvent(SetupEvent.OpenProtectedDirectories) }
        SettingLine(
            "Trash retention",
            state.trashRetention.label,
        ) { onEvent(SetupEvent.OpenTrashRetention) }
        SettingLine(
            "Import strategy",
            state.importStrategy.label(),
        ) { onEvent(SetupEvent.OpenImportStrategy) }
        SettingLine(
            "Storage status",
            state.rootCapability.label(),
        ) { onEvent(SetupEvent.OpenStorageStatus) }
        SettingLine(
            "Trash",
            "Empty",
        ) { onEvent(SetupEvent.OpenTrash) }
        SettingLine(
            "Recovery",
            "No pending work",
        ) { onEvent(SetupEvent.OpenRecovery) }
        SettingLine(
            "Notifications",
            state.notificationPreference.label,
        ) { onEvent(SetupEvent.OpenNotifications) }
    }
}

@Composable
private fun ScanScopesContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) = DetailPage(
    title = "Scan scopes",
    detail = "Select directories to include in future scans.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
) {
    state.scopeOptions.forEach { option ->
        ToggleRow(
            label = option.label,
            detail = option.detail,
            checked = option.id in state.selectedScopeIds,
            onClick = { onEvent(SetupEvent.ToggleScope(option.id)) },
        )
    }
    MediaDiscoverySection(state, onEvent)
}

@Composable
private fun MediaDiscoverySection(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "Download batches",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    val status = when (state.sourceScanState) {
        SourceScanUiState.SCANNING -> "Scanning..."
        SourceScanUiState.FAILED -> "Scan failed"
        SourceScanUiState.READY -> "${state.discoveredBatches.size} batches found"
        SourceScanUiState.IDLE -> when (state.mediaAccess) {
            MediaAccessState.PARTIAL -> "Selected photos only"
            MediaAccessState.DENIED -> "Photo access denied"
            MediaAccessState.FULL -> "Photo access granted"
            MediaAccessState.REQUESTING -> "Waiting for permission"
            MediaAccessState.NOT_REQUESTED -> "Photo access not requested"
        }
    }
    Text(
        text = status,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { onEvent(SetupEvent.RequestMediaAccess) },
        enabled = state.rootCapability == RootCapability.READY &&
            state.selectedRootRelativePath != null &&
            state.mediaAccess != MediaAccessState.REQUESTING &&
            state.sourceScanState != SourceScanUiState.SCANNING,
    ) {
        Text(if (state.sourceScanState == SourceScanUiState.FAILED) "Try again" else "Scan downloads")
    }
    state.discoveredBatches.forEach { batch ->
        ToggleRow(
            label = batch.label,
            detail = "${batch.imageCount} images",
            checked = batch.stableKey in state.selectedBatchKeys,
            onClick = { onEvent(SetupEvent.ToggleDiscoveredBatch(batch.stableKey)) },
        )
    }
}

@Composable
private fun ArchiveLocationContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) = DetailPage(
    title = "Archive location",
    detail = "Choose a folder and grant persistent access.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
) {
    ChoiceRow(
        label = "Create a new archive",
        detail = "Use a new organizer-owned folder after validation.",
        selected = state.rootSelection == RootSelection.NEW_REQUESTED,
        onClick = { onEvent(SetupEvent.ChooseNewRoot) },
    )
    ChoiceRow(
        label = "Adopt an existing archive",
        detail = "Validate an existing organizer folder before use.",
        selected = state.rootSelection == RootSelection.EXISTING_REQUESTED,
        onClick = { onEvent(SetupEvent.ChooseExistingRoot) },
    )
    if (state.rootSelection == RootSelection.EXISTING_REQUESTED) {
        AdoptionChecklist(state.rootAdoptionState)
        state.adoptionAlbumCount?.let { StatusValueLine("Albums found", it.toString()) }
        state.adoptionImportingCount?.let {
            StatusValueLine("Recovery needed", it.toString())
        }
    }
    state.selectedRootLabel?.let { StatusValueLine("Selected folder", it) }
    RootStatusNotice(state)
}

@Composable
private fun ProtectedDirectoriesContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) = DetailPage(
    title = "Protected directories",
    detail = "Selected directories will be excluded from future organization plans.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
) {
    ToggleRow(
        "Archive root",
        "Protect the planned organizer archive.",
        "archive" in state.protectedDirectoryIds,
    ) { onEvent(SetupEvent.ToggleProtectedDirectory("archive")) }
    ToggleRow(
        "Screenshots",
        "Keep screenshots outside automatic organization.",
        "screenshots" in state.protectedDirectoryIds,
    ) { onEvent(SetupEvent.ToggleProtectedDirectory("screenshots")) }
    ToggleRow(
        "Downloads",
        "Keep downloaded images outside automatic organization.",
        "downloads" in state.protectedDirectoryIds,
    ) { onEvent(SetupEvent.ToggleProtectedDirectory("downloads")) }
}

@Composable
private fun TrashRetentionContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) = DetailPage(
    title = "Trash retention",
    detail = "Choose how long future archived trash metadata should be retained.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
) {
    TrashRetention.entries.forEach { retention ->
        ChoiceRow(
            label = retention.label,
            detail = "Keep recovery metadata for ${retention.label}.",
            selected = state.trashRetention == retention,
            onClick = { onEvent(SetupEvent.SetTrashRetention(retention)) },
        )
    }
}

@Composable
private fun StorageStatusContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) = DetailPage(
    title = "Storage status",
    detail = "Review the selected archive folder and access state.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
) {
    StatusValueLine("Capability", state.rootCapability.label())
    SettingLine("Archive mode", state.rootSelection.label()) {
        onEvent(SetupEvent.OpenArchiveLocation)
    }
    StatusValueLine("Adoption checks", state.rootAdoptionState.label())
    state.selectedRootLabel?.let { StatusValueLine("Selected folder", it) }
    RootStatusNotice(state)
}

@Composable
private fun TrashContent(
    onEvent: (SetupEvent) -> Unit,
) = BlockedWorkspaceContent(
    title = "Trash",
    detail = "Items moved by future organization plans will appear here.",
    emptyTitle = "Trash is empty",
    emptyDetail = "Trash moves and permanent deletion remain disabled until device validation.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
)

@Composable
private fun RecoveryContent(
    onEvent: (SetupEvent) -> Unit,
) = BlockedWorkspaceContent(
    title = "Recovery",
    detail = "Interrupted operations and pending cleanup authorization will appear here.",
    emptyTitle = "No recovery work",
    emptyDetail = "Journal and recovery execution remain disabled until device validation.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
)

@Composable
private fun NotificationsContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) = DetailPage(
    title = "Notifications",
    detail = "Choose which future operation updates should be eligible for notifications.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
) {
    NotificationPreference.entries.forEach { preference ->
        ChoiceRow(
            label = preference.label,
            detail = preference.detail(),
            selected = state.notificationPreference == preference,
            onClick = { onEvent(SetupEvent.SetNotificationPreference(preference)) },
        )
    }
    Text(
        text = "Android notification permission has not been requested.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(StatusAmberSurface)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = StatusAmber,
    )
}

@Composable
private fun ImportStrategyContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) = DetailPage(
    title = "Import strategy",
    detail = "Choose the default behavior for future archive plans.",
    onBack = { onEvent(SetupEvent.BackToSettings) },
) {
    listOf(
        ImportStrategy.COPY_KEEP_SOURCE,
        ImportStrategy.MOVE_SOURCE,
    ).forEach { strategy ->
        ChoiceRow(
            label = strategy.label(),
            detail = strategy.detail(),
            selected = state.importStrategy == strategy,
            onClick = { onEvent(SetupEvent.SetImportStrategy(strategy)) },
        )
    }
    Text(
        text = "Move requires a verified archive commit and separate source-cleanup authorization.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(StatusAmberSurface)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = StatusAmber,
    )
}

@Composable
private fun BlockedWorkspaceContent(
    title: String,
    detail: String,
    emptyTitle: String,
    emptyDetail: String,
    onBack: () -> Unit,
) = DetailPage(
    title = title,
    detail = detail,
    onBack = onBack,
) {
    Text(
        text = emptyTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = emptyDetail,
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailPage(
    title: String,
    detail: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
        TextButton(onClick = onBack) { Text("< Settings") }
        PageTitle(title, detail)
        content()
    }
}

@Composable
private fun PageTitle(title: String, detail: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = detail,
        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingLine(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            "$value  >",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun AdoptionChecklist(state: RootAdoptionState) {
    Text(
        text = "Adoption checks",
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    StatusValueLine("Root marker", state.checkLabel())
    StatusValueLine("Album manifests", state.checkLabel())
    StatusValueLine("Workspace conflicts", state.checkLabel())
}

@Composable
private fun StatusValueLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun RootStatusNotice(state: SetupUiState) {
    val message = when (state.notice) {
        SetupNotice.SelectionCancelled -> "Folder selection was cancelled."
        SetupNotice.RootRejected ->
            "Archive access is unavailable or failed validation. Choose another folder."
        SetupNotice.RootRequired -> "Select an archive folder before finishing setup."
        null -> when (state.rootCapability) {
            RootCapability.UNSELECTED -> "No archive folder selected."
            RootCapability.SELECTING -> "Waiting for folder selection."
            RootCapability.READY -> "Persistent folder access is ready."
            RootCapability.FAILED -> "Folder access is unavailable."
        }
    }
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(StatusAmberSurface)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = StatusAmber,
    )
}

private fun RootSelection.label(): String = when (this) {
    RootSelection.NONE -> "Not selected"
    RootSelection.NEW_REQUESTED -> "New archive"
    RootSelection.EXISTING_REQUESTED -> "Existing archive"
}

private fun RootAdoptionState.label(): String = when (this) {
    RootAdoptionState.NOT_REQUESTED -> "Not selected"
    RootAdoptionState.NOT_APPLICABLE -> "Not applicable"
    RootAdoptionState.SELECTING -> "Selecting"
    RootAdoptionState.ACCESS_GRANTED -> "Access granted; checks pending"
    RootAdoptionState.VALIDATING -> "Validating"
    RootAdoptionState.VERIFIED -> "Verified"
    RootAdoptionState.FAILED -> "Access failed"
}

private fun RootAdoptionState.checkLabel(): String = when (this) {
    RootAdoptionState.SELECTING -> "Waiting"
    RootAdoptionState.ACCESS_GRANTED -> "Not checked"
    RootAdoptionState.VALIDATING -> "Checking"
    RootAdoptionState.VERIFIED -> "Verified"
    RootAdoptionState.FAILED -> "Unavailable"
    RootAdoptionState.NOT_REQUESTED -> "Not selected"
    RootAdoptionState.NOT_APPLICABLE -> "Not applicable"
}

private fun RootCapability.label(): String = when (this) {
    RootCapability.UNSELECTED -> "Not selected"
    RootCapability.SELECTING -> "Selecting"
    RootCapability.READY -> "Ready"
    RootCapability.FAILED -> "Unavailable"
}

private fun NotificationPreference.detail(): String = when (this) {
    NotificationPreference.OFF -> "Do not show operation notifications."
    NotificationPreference.OPERATION_STATUS -> "Allow completion and failure updates."
    NotificationPreference.STATUS_AND_CLEANUP ->
        "Also allow reminders for pending cleanup authorization."
}

private fun ImportStrategy.label(): String = when (this) {
    ImportStrategy.COPY_KEEP_SOURCE -> "Copy and keep source"
    ImportStrategy.MOVE_SOURCE -> "Archive, then request cleanup"
    ImportStrategy.INDEX_IN_PLACE -> "Index in place"
}

private fun ImportStrategy.detail(): String = when (this) {
    ImportStrategy.COPY_KEEP_SOURCE ->
        "Future plans copy verified files into the archive and retain their sources."
    ImportStrategy.MOVE_SOURCE ->
        "Future plans archive first, then request separate authorization to clean sources."
    ImportStrategy.INDEX_IN_PLACE ->
        "Advanced mode is unavailable in the P0 setup."
}
