package com.galleryorganizer.spikes.suite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.galleryorganizer.spikes.s01.S01ProbeActivity
import com.galleryorganizer.spikes.s02.S02ProbeActivity
import com.galleryorganizer.spikes.s02.fixture.ExternalFixtureContract
import com.galleryorganizer.spikes.s04.S04ProbeActivity

class ProbeSuiteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val externalToken = intent.getStringExtra(ExternalFixtureContract.TOKEN_EXTRA)
        if (
            savedInstanceState == null &&
            intent.action == ExternalFixtureContract.OPEN_ACTION &&
            externalToken != null &&
            ExternalFixtureContract.isValidToken(externalToken)
        ) {
            startActivity(
                Intent(this, S02ProbeActivity::class.java)
                    .putExtra(ExternalFixtureContract.TOKEN_EXTRA, externalToken),
            )
        }
        setContent {
            MaterialTheme {
                Surface {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Probe suite", style = MaterialTheme.typography.headlineSmall)
                        ProbeButton("S01 / SAF") { open(S01ProbeActivity::class.java) }
                        ProbeButton("S02 / MediaStore") { open(S02ProbeActivity::class.java) }
                        ProbeButton("S04 / Manifest atomicity") {
                            open(S04ProbeActivity::class.java)
                        }
                    }
                }
            }
        }
    }

    private fun open(target: Class<out ComponentActivity>) =
        startActivity(Intent(this, target))
}

@Composable
private fun ProbeButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
