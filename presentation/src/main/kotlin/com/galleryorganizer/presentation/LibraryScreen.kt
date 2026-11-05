package com.galleryorganizer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galleryorganizer.domain.ImportStrategy
import com.galleryorganizer.domain.SourceDecisionValue

private val WorkspaceAmber = Color(0xFF8A5A00)
private val WorkspaceAmberSurface = Color(0xFFFFE8B2)

@Composable
internal fun AlbumLibraryContent(
    setupState: SetupUiState,
    libraryState: LibraryUiState,
    inboxState: InboxUiState,
    onLibraryEvent: (LibraryEvent) -> Unit,
    onSetupEvent: (SetupEvent) -> Unit,
) {
    val selectionSummary = libraryState.selectionSummary
    val workspaceReview = WorkspaceReviewSnapshot.from(setupState, libraryState, inboxState)

    Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
        WorkspaceTitle(
            title = "Library",
            detail = "Search and organize albums stored in your local workspace.",
        )
        OutlinedTextField(
            value = libraryState.searchQuery,
            onValueChange = { onLibraryEvent(LibraryEvent.SearchChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search albums") },
            singleLine = true,
            trailingIcon = {
                if (libraryState.searchQuery.isNotEmpty()) {
                    TextButton(onClick = { onLibraryEvent(LibraryEvent.ClearSearch) }) {
                        Text("Clear")
                    }
                }
            },
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = libraryState.filter == filter,
                    onClick = { onLibraryEvent(LibraryEvent.FilterChanged(filter)) },
                    label = { Text(filter.label) },
                )
            }
        }
        LibrarySortMenu(libraryState.sort, onLibraryEvent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "${libraryState.visibleAlbums.size} albums",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Selected ${selectionSummary.selectionCount} of " +
                libraryState.previewAlbums.size,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        libraryState.visibleAlbums.forEach { album ->
            PreviewAlbumRow(
                album = album,
                selected = selectionSummary.selectedAlbums.any {
                    it.albumId == album.albumId
                },
                onClick = {
                    onLibraryEvent(LibraryEvent.ToggleAlbumSelection(album.albumId))
                },
            )
        }
        if (libraryState.loadState == LibraryLoadState.EMPTY) {
            Text(
                text = "No archived albums",
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Albums will appear after an archive plan completes.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selectionSummary.hasSelection) {
            TextButton(
                onClick = { onLibraryEvent(LibraryEvent.ClearSelection) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Clear selection")
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Album details",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            selectionSummary.selectedAlbums.forEach { album ->
                WorkspaceStatusLine(
                    album.title,
                    "${album.images.size} images · ${album.state.name}",
                )
            }
            libraryState.editableAlbum?.let { album ->
                Text(
                    text = "Metadata order",
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                album.images.forEachIndexed { index, image ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            image.fileName,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        )
                        TextButton(
                            onClick = {
                                onLibraryEvent(
                                    LibraryEvent.MoveImage(album.albumId, image.imageId, -1),
                                )
                            },
                            enabled = index > 0,
                        ) { Text("Up") }
                        TextButton(
                            onClick = {
                                onLibraryEvent(
                                    LibraryEvent.MoveImage(album.albumId, image.imageId, 1),
                                )
                            },
                            enabled = index < album.images.lastIndex,
                        ) { Text("Down") }
                    }
                    HorizontalDivider()
                }
                OutlinedButton(
                    onClick = { onLibraryEvent(LibraryEvent.CreateMetadataPlan) },
                    enabled = libraryState.hasMetadataChanges &&
                        libraryState.metadataPlanStatus in setOf(
                            MetadataPlanStatus.IDLE,
                            MetadataPlanStatus.ERROR,
                        ),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(
                        if (libraryState.metadataPlanStatus == MetadataPlanStatus.SAVING) {
                            "Saving..."
                        } else {
                            "Create metadata plan"
                        },
                    )
                }
                if (libraryState.metadataPlanStatus != MetadataPlanStatus.IDLE) {
                    Text(
                        text = libraryState.metadataPlanStatus.name.lowercase(),
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        WorkspaceReviewSnapshotSection(workspaceReview)
        WorkspaceGateNotice(setupState.rootCapability)
        OutlinedButton(
            onClick = { onSetupEvent(SetupEvent.OpenSettings) },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Review settings")
        }
    }
}

@Composable
private fun PreviewAlbumRow(
    album: PreviewAlbum,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(if (selected) "Selected" else "Select") },
            )
        }
        Text(
            text = album.dateLabel,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (album.needsReview) {
            Text(
                text = "Needs review",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = WorkspaceAmber,
            )
        }
    }
    HorizontalDivider()
}

@Composable
internal fun InboxContent(
    setupState: SetupUiState,
    inboxState: InboxUiState,
    libraryState: LibraryUiState,
    onSetupEvent: (SetupEvent) -> Unit,
    onInboxEvent: (InboxEvent) -> Unit,
    onOpenImageViewer: (String) -> Unit,
    thumbnails: Map<String, ImageBitmap>,
) {
    val decisionSummary = inboxState.decisionSummary
    val workspaceReview = WorkspaceReviewSnapshot.from(setupState, libraryState, inboxState)

    Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
        WorkspaceTitle(
            title = "Inbox",
            detail = "Review source batches before creating an archive plan.",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            inboxState.batches.forEach { batch ->
                FilterChip(
                    selected = inboxState.selectedBatchKey == batch.batchKey,
                    onClick = { onInboxEvent(InboxEvent.SelectBatch(batch.batchKey)) },
                    label = { Text("${batch.label} (${batch.imageCount})") },
                )
            }
        }
        Text(
            text = "Source images",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        when (inboxState.loadState) {
            InboxLoadState.IDLE -> InboxStatus("Select a source batch.")
            InboxLoadState.LOADING -> InboxStatus("Loading images...")
            InboxLoadState.EMPTY -> InboxStatus(
                if (inboxState.batches.isEmpty()) {
                    "No scanned source batches."
                } else {
                    "This batch has no images."
                },
            )
            InboxLoadState.ERROR -> {
                InboxStatus("Images could not be loaded.")
                TextButton(onClick = { onInboxEvent(InboxEvent.RetryPage) }) {
                    Text("Retry")
                }
            }
            InboxLoadState.STORAGE_DEFERRED,
            InboxLoadState.READY,
            InboxLoadState.APPENDING,
            -> Unit
        }
        WorkspaceStatusLine(
            "Decision summary",
            "Keep ${decisionSummary.keepCount} · " +
                "Review ${decisionSummary.reviewCount} · " +
                "Trash ${decisionSummary.trashCount}",
        )
        WorkspaceStatusLine("Undecided", decisionSummary.undecidedCount.toString())
        WorkspaceStatusLine(
            "Decision coverage",
            "${decisionSummary.decidedCount} / " +
                inboxState.previewItems.size +
                " · " + decisionSummary.status.reviewLabel(),
        )
        inboxState.previewItems.forEach { item ->
            InboxPreviewRow(
                item = item,
                selected = inboxState.decisionsBySourceId[item.sourceId],
                thumbnail = thumbnails[item.sourceId],
                onEvent = onInboxEvent,
                onOpen = { onOpenImageViewer(item.sourceId) },
            )
        }
        if (inboxState.hasNextPage ||
            inboxState.loadState == InboxLoadState.APPENDING
        ) {
            TextButton(
                onClick = { onInboxEvent(InboxEvent.LoadNextPage) },
                enabled = inboxState.loadState != InboxLoadState.APPENDING,
            ) {
                Text(
                    if (inboxState.loadState == InboxLoadState.APPENDING) {
                        "Loading..."
                    } else {
                        "Load more"
                    },
                )
            }
        }
        if (inboxState.previewItems.isNotEmpty()) {
            WorkspaceReviewSnapshotSection(workspaceReview)
        }
        OutlinedButton(
            onClick = { onSetupEvent(SetupEvent.OpenScanScopes) },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Review scan scopes")
        }
    }
}

@Composable
private fun InboxPreviewRow(
    item: InboxPreviewItem,
    selected: SourceDecisionValue?,
    thumbnail: ImageBitmap?,
    onEvent: (InboxEvent) -> Unit,
    onOpen: () -> Unit,
) {
    val previewModifier = Modifier
        .size(72.dp)
        .clip(RoundedCornerShape(4.dp))
        .clickable(onClick = onOpen)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (thumbnail == null) {
            Box(
                modifier = previewModifier
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Image(
                bitmap = thumbnail,
                contentDescription = "Open ${item.label}",
                modifier = previewModifier,
                contentScale = ContentScale.Crop,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = item.detail,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceDecisionValue.entries.forEach { decision ->
                    FilterChip(
                        selected = selected == decision,
                        onClick = {
                            if (selected != decision) {
                            onEvent(InboxEvent.SetDecision(item.sourceId, decision))
                            }
                        },
                        label = { Text(decision.label()) },
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun InboxStatus(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LibrarySortMenu(
    selected: LibrarySort,
    onLibraryEvent: (LibraryEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(top = 4.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text("Sort: ${selected.label}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        onLibraryEvent(LibraryEvent.SortChanged(sort))
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun SourceDecisionValue.label(): String = when (this) {
    SourceDecisionValue.KEEP -> "Keep"
    SourceDecisionValue.REVIEW -> "Review"
    SourceDecisionValue.TRASH -> "Trash"
}

private fun InboxDraftStatus.reviewLabel(): String = when (this) {
    InboxDraftStatus.NOT_STARTED -> "Not started"
    InboxDraftStatus.IN_PROGRESS -> "In progress"
    InboxDraftStatus.COMPLETE -> "Complete"
}

private fun ImportStrategy.reviewLabel(): String = when (this) {
    ImportStrategy.COPY_KEEP_SOURCE -> "Copy and keep source"
    ImportStrategy.MOVE_SOURCE -> "Move source"
    ImportStrategy.INDEX_IN_PLACE -> "Index in place"
}

private fun WorkspaceAttentionKind.reviewLabel(): String = when (this) {
    WorkspaceAttentionKind.ALBUM_NEEDS_REVIEW -> "Album needs review"
    WorkspaceAttentionKind.SOURCE_REVIEW -> "Source marked Review"
    WorkspaceAttentionKind.SOURCE_TRASH -> "Source marked Trash"
}

@Composable
private fun WorkspaceReviewSnapshotSection(snapshot: WorkspaceReviewSnapshot) {
    val attentionSummary = snapshot.attentionSummary

    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(
        text = "Workspace review",
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "Combined memory preview of library selection and inbox decisions. " +
            "It cannot create or run a plan.",
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    WorkspaceStatusLine("Selected albums", snapshot.selectedAlbumCount.toString())
    WorkspaceStatusLine(
        "Selected needing review",
        snapshot.selectedNeedsReviewAlbumCount.toString(),
    )
    WorkspaceStatusLine(
        "Source decisions",
        "Keep ${snapshot.keepSourceCount} · " +
            "Review ${snapshot.reviewSourceCount} · " +
            "Trash ${snapshot.trashSourceCount}",
    )
    WorkspaceStatusLine(
        "Decided sources",
        "${snapshot.decidedSourceCount} / " +
            (snapshot.decidedSourceCount + snapshot.undecidedSourceCount),
    )
    WorkspaceStatusLine("Inbox draft", snapshot.inboxDraftStatus.reviewLabel())
    WorkspaceStatusLine(
        "Scan scopes",
        snapshot.setupSummary.selectedScopeCount.toString(),
    )
    WorkspaceStatusLine(
        "Import strategy",
        snapshot.setupSummary.importStrategy.reviewLabel(),
    )
    WorkspaceStatusLine(
        "Trash retention",
        snapshot.setupSummary.trashRetention.label,
    )
    WorkspaceStatusLine(
        "Attention items",
        attentionSummary.attentionItemCount.toString(),
    )
    WorkspaceStatusLine(
        "Trash preview",
        if (attentionSummary.hasTrashPreview) {
            "${attentionSummary.trashSourceCount} source decisions"
        } else {
            "None"
        },
    )
    snapshot.attentionItems.forEach { item ->
        WorkspaceStatusLine(
            item.label,
            "${item.kind.reviewLabel()} · ${item.detail}",
        )
    }
}

@Composable
private fun WorkspaceTitle(title: String, detail: String) {
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
private fun WorkspaceStatusLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun WorkspaceGateNotice(rootCapability: RootCapability) {
    Text(
        text = if (rootCapability == RootCapability.READY) {
            "Archive access is ready. Scanning and file changes are not enabled."
        } else {
            "Archive access is unavailable. Scanning and file changes are not enabled."
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .background(WorkspaceAmberSurface)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = WorkspaceAmber,
    )
}
