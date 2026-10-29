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

private val WorkspaceAmber = Color(0xFF8A5A00)
private val WorkspaceAmberSurface = Color(0xFFFFE8B2)

@Composable
internal fun AlbumLibraryContent(
    libraryState: LibraryUiState,
    onLibraryEvent: (LibraryEvent) -> Unit,
    onSetupEvent: (SetupEvent) -> Unit,
) {
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
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Text(
            text = "No albums",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Album data will appear after storage validation and the first scan.",
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
internal fun InboxContent(
    setupState: SetupUiState,
    onSetupEvent: (SetupEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)) {
        WorkspaceTitle(
            title = "Inbox",
            detail = "Review source batches before creating an archive plan.",
        )
        WorkspaceStatusLine("Coverage", "Not scanned")
        WorkspaceStatusLine("Scan scopes", "${setupState.selectedScopeIds.size} selected")
        WorkspaceStatusLine("Pending batches", "0")
        WorkspaceGateNotice()
        OutlinedButton(
            onClick = { onSetupEvent(SetupEvent.OpenScanScopes) },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Review scan scopes")
        }
    }
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
