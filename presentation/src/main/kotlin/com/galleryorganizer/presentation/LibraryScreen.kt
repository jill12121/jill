package com.galleryorganizer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.galleryorganizer.domain.SourceDecisionValue

private val WorkspaceAmber = Color(0xFF8A5A00)
private val WorkspaceAmberSurface = Color(0xFFFFE8B2)

@Composable
internal fun AlbumLibraryContent(
    libraryState: LibraryUiState,
    onLibraryEvent: (LibraryEvent) -> Unit,
    onSetupEvent: (SetupEvent) -> Unit,
) {
    val selectionSummary = libraryState.selectionSummary

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
            text = "Memory preview · ${libraryState.visibleAlbums.size} albums",
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
        if (selectionSummary.hasSelection) {
            TextButton(
                onClick = { onLibraryEvent(LibraryEvent.ClearSelection) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Clear selection")
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Selection preview",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "These selected examples are memory-only and cannot create an archive plan.",
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selectionSummary.selectedAlbums.forEach { album ->
                WorkspaceStatusLine(
                    album.title,
                    album.dateLabel +
                        if (album.needsReview) " · Needs review" else " · Ready",
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Text(
            text = "These examples are not scanned albums.",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkspaceGateNotice()
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
    onSetupEvent: (SetupEvent) -> Unit,
    onInboxEvent: (InboxEvent) -> Unit,
) {
    val decisionSummary = inboxState.decisionSummary

    Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
        WorkspaceTitle(
            title = "Inbox",
            detail = "Review source batches before creating an archive plan.",
        )
        WorkspaceStatusLine("Coverage", "Not scanned")
        WorkspaceStatusLine("Scan scopes", "${setupState.selectedScopeIds.size} selected")
        WorkspaceStatusLine("Pending batches", "0")
        WorkspaceGateNotice()
        Text(
            text = "Decision preview",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Try source decisions with example items. This draft is memory-only and cannot create or run a plan.",
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                " · " + when (decisionSummary.status) {
                    InboxDraftStatus.NOT_STARTED -> "Not started"
                    InboxDraftStatus.IN_PROGRESS -> "In progress"
                    InboxDraftStatus.COMPLETE -> "Complete"
                },
        )
        inboxState.previewItems.forEach { item ->
            InboxPreviewRow(
                item = item,
                selected = inboxState.decisionsBySourceId[item.sourceId],
                onEvent = onInboxEvent,
            )
        }
        if (inboxState.decisionsBySourceId.isNotEmpty()) {
            TextButton(
                onClick = { onInboxEvent(InboxEvent.ClearDraft) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Clear preview decisions")
            }
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Decision review",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "These decisions are memory-only and cannot create an archive plan.",
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            inboxState.decisionReviewItems.forEach { reviewItem ->
                WorkspaceStatusLine(
                    reviewItem.item.label,
                    "${reviewItem.decision.label()} · " +
                        reviewItem.item.detail,
                )
            }
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
    onEvent: (InboxEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(item.label, style = MaterialTheme.typography.titleMedium)
        Text(
            text = item.detail,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceDecisionValue.entries.forEach { decision ->
                FilterChip(
                    selected = selected == decision,
                    onClick = {
                        if (selected == decision) {
                            onEvent(InboxEvent.ClearDecision(item.sourceId))
                        } else {
                            onEvent(InboxEvent.SetDecision(item.sourceId, decision))
                        }
                    },
                    label = { Text(decision.label()) },
                )
            }
        }
    }
    HorizontalDivider()
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
private fun WorkspaceGateNotice() {
    Text(
        text = "Device validation is deferred. Scanning and storage remain unavailable.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .background(WorkspaceAmberSurface)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = WorkspaceAmber,
    )
}
