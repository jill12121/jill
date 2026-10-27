package com.galleryorganizer.spikes.s02

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID

data class CleanupAuthorization(
    val pendingIntent: PendingIntent,
    val sampleUri: Uri,
)

data class S02ProbeRun(
    val result: S02ProbeResult,
    val cleanupAuthorization: CleanupAuthorization? = null,
)

class MediaStoreProbe(private val context: Context) {
    private val resolver = context.contentResolver

    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun permissionState(requestAttempted: Boolean): MediaPermissionState {
        val fullPermission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (context.checkSelfPermission(fullPermission) == PackageManager.PERMISSION_GRANTED) {
            return MediaPermissionState.FULL
        }
        if (
            Build.VERSION.SDK_INT >= 34 &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return MediaPermissionState.PARTIAL_USER_SELECTED
        }
        return if (requestAttempted) {
            MediaPermissionState.DENIED
        } else {
            MediaPermissionState.NOT_GRANTED
        }
    }

    suspend fun run(requestAttempted: Boolean): S02ProbeRun = withContext(Dispatchers.IO) {
        val permission = permissionState(requestAttempted)
        if (
            permission == MediaPermissionState.NOT_GRANTED ||
            permission == MediaPermissionState.DENIED
        ) {
            return@withContext S02ProbeRun(emptyResult(permission))
        }

        var sample: Uri? = null
        try {
            val pages = queryPages()
            val totalRows = pages.sumOf(PageEvidence::rowCount)
            var result = S02ProbeResult(
                permission,
                totalRows,
                pages,
                CleanupAuthorizationState.NOT_RUN,
                emptyList(),
            )
            if (result.outcome == S02SingleDeviceOutcome.FAIL) {
                return@withContext S02ProbeRun(result)
            }

            sample = createControlledSample()
            val request = MediaStore.createDeleteRequest(resolver, listOf(sample))
            result = result.copy(
                cleanupState = CleanupAuthorizationState.PENDING,
                authorizationBatchSizes = listOf(1),
            )
            S02ProbeRun(result, CleanupAuthorization(request, sample))
        } catch (caught: Exception) {
            sample?.let(::deleteOwnedSample)
            S02ProbeRun(
                emptyResult(permission).copy(
                    cleanupState = CleanupAuthorizationState.REQUEST_FAILED,
                    exceptionClass = caught.javaClass.simpleName,
                ),
            )
        }
    }

    suspend fun completeCleanup(
        run: S02ProbeRun,
        granted: Boolean,
    ): S02ProbeResult = withContext(Dispatchers.IO) {
        val authorization = run.cleanupAuthorization ?: return@withContext run.result
        val stillPresent = exists(authorization.sampleUri)
        val state = when {
            !granted -> CleanupAuthorizationState.DENIED
            stillPresent -> CleanupAuthorizationState.GRANTED_STILL_PRESENT
            else -> CleanupAuthorizationState.GRANTED_REMOVED
        }
        if (stillPresent) deleteOwnedSample(authorization.sampleUri)
        run.result.copy(cleanupState = state)
    }

    private fun queryPages(): List<PageEvidence> {
        val pages = mutableListOf<PageEvidence>()
        val seen = mutableSetOf<Long>()
        var lastId = 0L
        while (true) {
            val args = Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Images.Media._ID} > ?",
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(lastId.toString()),
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media._ID),
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_ASCENDING,
                )
                putInt(
                    ContentResolver.QUERY_ARG_LIMIT,
                    PaginationEvidenceValidator.PAGE_SIZE,
                )
            }
            val started = System.nanoTime()
            var firstId = 0L
            var pageLastId = 0L
            var count = 0
            var duplicates = 0
            val cursor = requireNotNull(
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    args,
                    null,
                ),
            )
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    if (count == 0) firstId = id
                    pageLastId = id
                    if (!seen.add(id)) duplicates++
                    count++
                }
            }
            if (count == 0) break
            pages += PageEvidence(
                pages.size,
                count,
                firstId,
                pageLastId,
                (System.nanoTime() - started) / 1_000_000,
                duplicates,
            )
            if (count != PaginationEvidenceValidator.PAGE_SIZE) break
            lastId = pageLastId
        }
        return pages
    }

    private fun createControlledSample(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ".s02-${UUID.randomUUID()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GalleryOrganizerProbe")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        )
        try {
            resolver.openOutputStream(uri, "w").use {
                requireNotNull(it).write(PROBE_PNG)
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        } catch (caught: Exception) {
            deleteOwnedSample(uri)
            throw caught
        }
    }

    private fun exists(uri: Uri): Boolean =
        resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)
            ?.use { it.moveToFirst() } == true

    private fun deleteOwnedSample(uri: Uri) {
        runCatching { resolver.delete(uri, null, null) }
    }

    private fun emptyResult(permission: MediaPermissionState) = S02ProbeResult(
        permission,
        0,
        emptyList(),
        CleanupAuthorizationState.NOT_RUN,
        emptyList(),
    )

    private companion object {
        val PROBE_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk" +
                "YAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
        )
    }
}

class S02ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = MediaStoreProbe(this)
        setContent {
            var run by remember { mutableStateOf<S02ProbeRun?>(null) }
            var running by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val cleanupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { response ->
                val pending = run ?: return@rememberLauncherForActivityResult
                scope.launch {
                    running = true
                    val completed = probe.completeCleanup(
                        pending,
                        response.resultCode == Activity.RESULT_OK,
                    )
                    run = pending.copy(result = completed, cleanupAuthorization = null)
                    running = false
                }
            }

            fun execute(requestAttempted: Boolean) {
                scope.launch {
                    running = true
                    val updated = probe.run(requestAttempted)
                    run = updated
                    updated.cleanupAuthorization?.let {
                        cleanupLauncher.launch(
                            IntentSenderRequest.Builder(it.pendingIntent).build(),
                        )
                    }
                    running = false
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { execute(true) }

            MaterialTheme {
                Surface {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("S02 / MediaStore", style = MaterialTheme.typography.headlineSmall)
                        Text("Runtime permission, bounded keyset pages, and controlled cleanup.")
                        Button(
                            onClick = {
                                val permission = probe.permissionState(false)
                                if (
                                    permission == MediaPermissionState.FULL ||
                                    permission == MediaPermissionState.PARTIAL_USER_SELECTED
                                ) {
                                    execute(false)
                                } else {
                                    permissionLauncher.launch(probe.requiredPermissions())
                                }
                            },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (running) "Running..." else "Run S02 probe") }
                        Text(run?.result?.toJson() ?: "PENDING")
                    }
                }
            }
        }
    }
}
