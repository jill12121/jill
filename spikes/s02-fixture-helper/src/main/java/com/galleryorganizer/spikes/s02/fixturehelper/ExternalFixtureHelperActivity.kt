package com.galleryorganizer.spikes.s02.fixturehelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
        val create = Button(this).apply { text = "Copy fixture token" }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                addView(status)
                addView(create)
            },
        )
        fun prepareAndCopyFixture() {
            if (!create.isEnabled) return
            create.isEnabled = false
            status.text = "Preparing helper-owned fixture..."
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { prepareFixture() } }
                    .onSuccess { prepared ->
                        if (copyToken(prepared.token)) {
                            status.text =
                                if (prepared.reused) {
                                    "Existing fixture token copied. Open Probe Suite > S02 and paste it."
                                } else {
                                    "Fixture created. Token copied. Open Probe Suite > S02 and paste it."
                                }
                            Toast.makeText(
                                this@ExternalFixtureHelperActivity,
                                "S02 fixture token copied",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            status.text =
                                "Fixture ready, but clipboard copy failed. Token: ${prepared.token}"
                            Toast.makeText(
                                this@ExternalFixtureHelperActivity,
                                "Clipboard copy failed",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                    .onFailure {
                        status.text = "Fixture preparation failed: ${it.javaClass.simpleName}"
                        Toast.makeText(
                            this@ExternalFixtureHelperActivity,
                            status.text,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                create.isEnabled = true
            }
        }
        create.setOnClickListener { prepareAndCopyFixture() }
        if (FixtureLaunchPolicy.shouldPrepareFixture(savedInstanceState != null)) {
            prepareAndCopyFixture()
        }
    }

    private fun prepareFixture(): PreparedFixture {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        val savedToken = preferences.getString(TOKEN_KEY, null)
        val validSavedToken = savedToken?.let(ExternalFixtureContract::isValidToken) == true
        val savedFixtureExists = validSavedToken && fixtureExists(requireNotNull(savedToken))
        return when (
            FixtureLaunchPolicy.tokenAction(validSavedToken, savedFixtureExists)
        ) {
            FixtureTokenAction.REUSE -> PreparedFixture(requireNotNull(savedToken), reused = true)
            FixtureTokenAction.CREATE -> PreparedFixture(createFixture(), reused = false)
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
            check(
                getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(TOKEN_KEY, token)
                    .commit(),
            )
            return token
        } catch (caught: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw caught
        }
    }

    private fun fixtureExists(token: String): Boolean = runCatching {
        val expectedPayload = ExternalFixtureContract.payload(token)
        val ids = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            listOf(
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                "${MediaStore.Images.Media.MIME_TYPE} = ?",
                "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ?",
                "${MediaStore.Images.Media.IS_PENDING} = 0",
            ).joinToString(" AND "),
            arrayOf(
                ExternalFixtureContract.displayName(token),
                ExternalFixtureContract.RELATIVE_PATH,
                ExternalFixtureContract.MIME_TYPE,
                ExternalFixtureContract.HELPER_PACKAGE,
            ),
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < 2) add(cursor.getLong(0))
            }
        }.orEmpty()
        if (ids.size != 1) return@runCatching false
        val uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ids.single(),
        )
        val actualPayload = runCatching {
            contentResolver.openInputStream(uri).use { requireNotNull(it).readBytes() }
        }.getOrNull()
        actualPayload?.contentEquals(expectedPayload) == true
    }.getOrDefault(false)

    private fun copyToken(token: String): Boolean = runCatching {
        requireNotNull(getSystemService(ClipboardManager::class.java))
            .setPrimaryClip(ClipData.newPlainText("s02-fixture-token", token))
    }.isSuccess

    private data class PreparedFixture(
        val token: String,
        val reused: Boolean,
    )

    private companion object {
        const val PREFERENCES_NAME = "s02-fixture-helper"
        const val TOKEN_KEY = "lastFixtureToken"
    }
}
