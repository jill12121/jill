package com.galleryorganizer.spikes.s01

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            val scope = rememberCoroutineScope()
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    preferences.edit().putString("treeUri", uri.toString()).apply()
                    scope.launch {
                        running = true
                        result = probe.run(uri)
                        preferences.edit().putString("result", result?.encode()).apply()
                        running = false
                    }
                }
            }

            MaterialTheme {
                Surface(color = Color(0xFFF7F9F7)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text("S01 / SAF", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Workspace capability",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Select Download first. The probe writes and removes one temporary file.",
                            color = Color(0xFF4C5B54),
                        )
                        Button(
                            onClick = { launcher.launch(null) },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (running) "Running probe..." else "Select root and run")
                        }
                        OutlinedButton(
                            onClick = {
                                val saved = preferences.getString("treeUri", null)?.let(Uri::parse)
                                val previous = result
                                if (saved != null && previous != null) {
                                    scope.launch {
                                        running = true
                                        result = previous.copy(
                                            savedGrantRestored = probe.verifySavedGrant(saved),
                                        )
                                        preferences.edit()
                                            .putString("result", result?.encode())
                                            .apply()
                                        running = false
                                    }
                                }
                            },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Verify saved authorization")
                        }
                        HorizontalDivider()
                        Text("Device result", style = MaterialTheme.typography.titleMedium)
                        result?.reportLines(Build.VERSION.SDK_INT)?.forEach { line ->
                            val parts = line.split('=', limit = 2)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(parts[0], color = Color(0xFF5F6D66))
                                Text(parts.getOrElse(1) { "" }, fontWeight = FontWeight.Medium)
                            }
                        } ?: Text("No probe result", color = Color(0xFF5F6D66))
                        Text(
                            "Force-stop and reopen the app before verifying saved authorization.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5F6D66),
                        )
                    }
                }
            }
        }
    }
}
