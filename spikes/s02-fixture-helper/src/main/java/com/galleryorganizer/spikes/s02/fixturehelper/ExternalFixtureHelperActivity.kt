package com.galleryorganizer.spikes.s02.fixturehelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.galleryorganizer.spikes.s02.fixture.ExternalFixtureContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ExternalFixtureHelperActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "Creates one helper-owned MediaStore fixture for the S02 probe."
        }
        val create = Button(this).apply { text = "Create fixture and copy token" }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                addView(status)
                addView(create)
            },
        )
        create.setOnClickListener {
            create.isEnabled = false
            status.text = "Creating helper-owned fixture..."
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { createFixture() } }
                    .onSuccess { token ->
                        if (copyToken(token)) {
                            status.text =
                                "Fixture created. Token copied. Open Probe Suite > S02 and paste it."
                        } else {
                            status.text = "Fixture created, but clipboard copy failed. Token: $token"
                        }
                    }
                    .onFailure { status.text = "Fixture creation failed: ${it.javaClass.simpleName}" }
                create.isEnabled = true
            }
        }
    }

    private fun createFixture(): String {
        val token = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ExternalFixtureContract.displayName(token))
            put(MediaStore.Images.Media.MIME_TYPE, ExternalFixtureContract.MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, ExternalFixtureContract.RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        )
        try {
            resolver.openOutputStream(uri, "w").use {
                requireNotNull(it).write(ExternalFixtureContract.payload(token))
            }
            check(
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                ) == 1,
            )
            return token
        } catch (caught: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw caught
        }
    }

    private fun copyToken(token: String): Boolean = runCatching {
        requireNotNull(getSystemService(ClipboardManager::class.java))
            .setPrimaryClip(ClipData.newPlainText("s02-fixture-token", token))
    }.isSuccess
}
