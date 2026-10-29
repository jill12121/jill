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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val AppBackground = Color(0xFFF6F7F4)
private val StatusAmber = Color(0xFF8A5A00)
private val StatusAmberSurface = Color(0xFFFFE8B2)

@Composable
fun SetupScreen(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = AppBackground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            AppHeader(state.destination, onEvent)
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            when (state.destination) {
                SetupDestination.ONBOARDING -> OnboardingContent(state, onEvent)
                SetupDestination.SETTINGS -> SettingsContent(state, onEvent)
            }
        }
    }
}

@Composable
private fun AppHeader(
    destination: SetupDestination,
    onEvent: (SetupEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        }
        TextButton(
            onClick = { onEvent(SetupEvent.OpenSetup) },
            enabled = destination != SetupDestination.ONBOARDING,
        ) {
            Text("Setup")
        }
        TextButton(
            onClick = { onEvent(SetupEvent.OpenSettings) },
            enabled = destination != SetupDestination.SETTINGS,
        ) {
            Text("Settings")
        }
    }
}

@Composable
private fun OnboardingContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = "Choose what to scan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Selections stay on this device. The organizer root is never scanned automatically.",
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.scopeOptions.forEach { option ->
            ScopeRow(
                option = option,
                selected = option.id in state.selectedScopeIds,
                onClick = { onEvent(SetupEvent.ToggleScope(option.id)) },
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Archive location",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Directory selection remains disabled until storage capability checks resume.",
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onEvent(SetupEvent.ChooseNewRoot) },
                enabled = false,
            ) {
                Text("Choose new")
            }
            OutlinedButton(
                onClick = { onEvent(SetupEvent.ChooseExistingRoot) },
                enabled = false,
            ) {
                Text("Use existing")
            }
        }
        GateNotice(visible = true)
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { onEvent(SetupEvent.Finish) },
            enabled = state.canFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Finish setup")
        }
    }
}

@Composable
private fun ScopeRow(
    option: ScanScopeOption,
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
        Checkbox(checked = selected, onCheckedChange = null)
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(option.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                option.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsContent(
    state: SetupUiState,
    onEvent: (SetupEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        SettingLine("Scan scopes", "${state.selectedScopeIds.size} selected")
        SettingLine("Organizer root", "Not configured")
        SettingLine("Storage capability", "Device checks deferred")
        SettingLine("Trash retention", "Not configured")
        GateNotice(visible = true)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { onEvent(SetupEvent.OpenSetup) }) {
            Text("Edit setup")
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
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
private fun GateNotice(visible: Boolean) {
    if (!visible) return
    Text(
        text = "Device validation deferred. Storage actions remain disabled.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(StatusAmberSurface)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = StatusAmber,
    )
}
