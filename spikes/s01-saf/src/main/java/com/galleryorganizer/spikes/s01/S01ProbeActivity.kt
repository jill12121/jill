package com.galleryorganizer.spikes.s01

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class S01ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("s01", MODE_PRIVATE)
        val probe = SafCapabilityProbe(this)
        setContent {
            var result by remember {
                mutableStateOf(S01ProbeResult.decode(preferences.getString("result", null)))
            }
            var running by remember { mutableStateOf(false) }
            var copyStatus by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()

            fun persist(updated: S01ProbeResult) {
                result = updated
                preferences.edit().putString("result", updated.encode()).apply()
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    val previous = preferences.getString("treeUri", null)?.let(Uri::parse)
                    scope.launch {
                        running = true
                        val updated = probe.run(uri)
                        if (probe.hasPersistedGrant(uri)) {
                            check(
                                preferences.edit().putString("treeUri", uri.toString()).commit(),
                            )
                            if (previous != null && previous != uri) {
                                probe.releasePersistedGrant(previous)
                            }
                        }
                        persist(updated)
                        running = false
                    }
                }
            }

            fun retry(markReboot: Boolean) {
                val saved = preferences.getString("treeUri", null)?.let(Uri::parse)
                val previous = result
                if (saved != null && previous != null) scope.launch {
                    running = true
                    val passed = probe.retrySavedGrant(saved)
                    persist(
                        if (markReboot) previous.copy(deviceRebootRetryPassed = passed)
                        else previous.copy(forceStopRetryPassed = passed),
                    )
                    running = false
                }
            }

            MaterialTheme {
                Surface(color = Color(0xFFF7F9F7)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("S01 / SAF", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Workspace capability probe",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Select the intended organizer root. The probe creates a random, " +
                                "ownership-verified sandbox inside it and never mutates siblings.",
                            color = Color(0xFF4C5B54),
                        )
                        Button(
                            onClick = { launcher.launch(DOWNLOADS_INITIAL_URI) },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (running) "Running..." else "Select organizer root and run")
                        }
                        OutlinedButton(
                            onClick = { retry(false) },
                            enabled = !running && result != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Run force-stop retry") }
                        OutlinedButton(
                            onClick = { retry(true) },
                            enabled = !running && result != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Run device-reboot retry") }
                        OutlinedButton(
                            onClick = {
                                result?.toJson()?.let { json ->
                                    copyStatus = if (ResultClipboard(this@S01ProbeActivity).copy(json)) {
                                        "Result JSON copied"
                                    } else {
                                        "Clipboard unavailable"
                                    }
                                }
                            },
                            enabled = result != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Copy result JSON") }
                        copyStatus?.let { Text(it, color = Color(0xFF4C5B54)) }
                        HorizontalDivider()
                        Text("Device result", style = MaterialTheme.typography.titleMedium)
                        result?.reportLines()?.forEach { line ->
                            val parts = line.split('=', limit = 2)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(parts[0], color = Color(0xFF5F6D66))
                                Text(parts.getOrElse(1) { "" }, fontWeight = FontWeight.Medium)
                            }
                        } ?: Text("PENDING", color = Color(0xFF5F6D66))
                    }
                }
            }
        }
    }

    private companion object {
        val DOWNLOADS_INITIAL_URI =
            Uri.parse("content://com.android.externalstorage.documents/root/downloads")
    }
}
