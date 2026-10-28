package com.galleryorganizer.spikes.s02

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.galleryorganizer.spikes.s02.fixture.ExternalFixtureContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class CleanupAuthorization(
    val pendingIntent: PendingIntent,
    val sampleUri: Uri,
)

data class S02ProbeRun(
    val result: S02ProbeResult,
    val cleanupAuthorization: CleanupAuthorization? = null,
)

class S02ProbeViewModel : ViewModel() {
    var run by mutableStateOf<S02ProbeRun?>(null)
        private set

    fun updateRun(updated: S02ProbeRun) {
        run = updated
    }

    fun completeCleanup(probe: MediaStoreProbe, granted: Boolean) {
        val pending = run ?: return
        viewModelScope.launch {
            val completed = probe.completeCleanup(pending, granted)
            if (
                CleanupCompletionGuard.isCurrent(
                    run?.result?.provenance?.runId,
                    pending.result.provenance.runId,
                )
            ) {
                run = pending.copy(result = completed, cleanupAuthorization = null)
            }
        }
    }
}

private enum class CleanupPresence { PRESENT, ABSENT, QUERY_FAILED }

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

    suspend fun run(
        requestAttempted: Boolean,
        externalToken: String,
    ): S02ProbeRun = withContext(Dispatchers.IO) {
        val permission = permissionState(requestAttempted)
        val provenance = provenance()
        if (
            permission == MediaPermissionState.NOT_GRANTED ||
            permission == MediaPermissionState.DENIED
        ) {
            return@withContext S02ProbeRun(emptyResult(permission, provenance))
        }

        try {
            val pages = queryPages()
            val totalRows = pages.sumOf(PageEvidence::rowCount)
            var result = S02ProbeResult(
                permission,
                totalRows,
                pages,
                CleanupAuthorizationState.NOT_RUN,
                emptyList(),
                provenance = provenance,
            )
            if (result.outcome == S02SingleDeviceOutcome.FAIL) {
                return@withContext S02ProbeRun(result)
            }

            val fixture = findExternalFixture(externalToken)
            val request = MediaStore.createDeleteRequest(resolver, listOf(fixture.first))
            result = result.copy(
                cleanupState = CleanupAuthorizationState.PENDING,
                authorizationBatchSizes = listOf(1),
                cleanupFixtureEvidence = fixture.second,
            )
            S02ProbeRun(result, CleanupAuthorization(request, fixture.first))
        } catch (caught: Exception) {
            S02ProbeRun(
                emptyResult(permission, provenance).copy(
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
        val presence = presenceOf(authorization.sampleUri)
        val state = when {
            !granted -> CleanupAuthorizationState.DENIED
            presence == CleanupPresence.PRESENT ->
                CleanupAuthorizationState.GRANTED_STILL_PRESENT
            presence == CleanupPresence.ABSENT ->
                CleanupAuthorizationState.GRANTED_REMOVED
            else -> CleanupAuthorizationState.GRANTED_QUERY_FAILED
        }
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
            var strictlyIncreasing = true
            var queryArgsHonored = false
            val cursor = requireNotNull(
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    args,
                    null,
                ),
            )
            cursor.use {
                val honored = it.extras
                    .getStringArray(ContentResolver.EXTRA_HONORED_ARGS)
                    ?.toSet()
                    .orEmpty()
                queryArgsHonored = REQUIRED_QUERY_ARGS.all(honored::contains)
                var previousId = lastId
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    if (id <= previousId) strictlyIncreasing = false
                    if (count == 0) firstId = id
                    pageLastId = id
                    if (!seen.add(id)) duplicates++
                    count++
                    check(count <= PaginationEvidenceValidator.PAGE_SIZE) {
                        "PAGE_SIZE_EXCEEDED"
                    }
                    check(strictlyIncreasing && duplicates == 0) {
                        "NON_MONOTONIC_PAGE"
                    }
                    previousId = id
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
                strictlyIncreasing,
                queryArgsHonored,
            )
            if (!queryArgsHonored) break
            if (count != PaginationEvidenceValidator.PAGE_SIZE) break
            check(pageLastId > lastId) { "PAGE_DID_NOT_ADVANCE" }
            lastId = pageLastId
        }
        return pages
    }

    private fun findExternalFixture(token: String): Pair<Uri, ExternalFixtureEvidence> {
        require(ExternalFixtureContract.isValidToken(token)) { "INVALID_FIXTURE_TOKEN" }
        val displayName = ExternalFixtureContract.displayName(token)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.OWNER_PACKAGE_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DISPLAY_NAME,
        )
        val matches = mutableListOf<Pair<Uri, ExternalFixtureEvidence>>()
        requireNotNull(
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                null,
            ),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0).toString(),
                )
                var evidence = ExternalFixtureEvidence(
                    ownerPackageName = cursor.getString(1).orEmpty(),
                    relativePath = cursor.getString(2).orEmpty(),
                    mimeType = cursor.getString(3).orEmpty(),
                    displayName = cursor.getString(4).orEmpty(),
                    token = token,
                    tokenMarkerMatches = false,
                )
                if (evidence.copy(tokenMarkerMatches = true).isValid) {
                    evidence = evidence.copy(tokenMarkerMatches = payloadMatches(uri, token))
                }
                matches += uri to evidence
            }
        }
        val fixture = matches.singleOrNull() ?: error("FIXTURE_NOT_UNIQUE")
        require(fixture.second.isValid) { "FIXTURE_PROVENANCE_INVALID" }
        return fixture
    }

    private fun payloadMatches(uri: Uri, token: String): Boolean {
        val expected = ExternalFixtureContract.payload(token)
        val actual = ByteArray(expected.size + 1)
        val count = resolver.openInputStream(uri)?.use { stream ->
            var offset = 0
            while (offset < actual.size) {
                val read = stream.read(actual, offset, actual.size - offset)
                if (read < 0) break
                offset += read
            }
            offset
        } ?: return false
        return count == expected.size && actual.copyOf(count).contentEquals(expected)
    }

    private fun presenceOf(uri: Uri): CleanupPresence = try {
        val cursor = resolver.query(
            uri,
            arrayOf(MediaStore.Images.Media._ID),
            null,
            null,
            null,
        ) ?: return CleanupPresence.QUERY_FAILED
        cursor.use {
            if (it.moveToFirst()) CleanupPresence.PRESENT else CleanupPresence.ABSENT
        }
    } catch (_: Exception) {
        CleanupPresence.QUERY_FAILED
    }

    @Suppress("DEPRECATION")
    private fun provenance(): S02ProbeProvenance {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return S02ProbeProvenance.fromRawFingerprint(
            runId = UUID.randomUUID().toString(),
            model = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            appVersionName = packageInfo.versionName ?: "UNKNOWN",
            appVersionCode = packageInfo.longVersionCode,
            rawFingerprint = Build.FINGERPRINT,
        )
    }

    private fun emptyResult(
        permission: MediaPermissionState,
        provenance: S02ProbeProvenance,
    ) = S02ProbeResult(
        permission,
        0,
        emptyList(),
        CleanupAuthorizationState.NOT_RUN,
        emptyList(),
        provenance = provenance,
    )

    private companion object {
        val REQUIRED_QUERY_ARGS = setOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            ContentResolver.QUERY_ARG_SORT_COLUMNS,
            ContentResolver.QUERY_ARG_SORT_DIRECTION,
            ContentResolver.QUERY_ARG_LIMIT,
        )
    }
}

class S02ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = MediaStoreProbe(applicationContext)
        val probeState = ViewModelProvider(this)[S02ProbeViewModel::class.java]
        val externalToken = intent.getStringExtra(ExternalFixtureContract.TOKEN_EXTRA).orEmpty()
        setContent {
            var running by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val cleanupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { response ->
                running = true
                probeState.completeCleanup(
                    probe,
                    response.resultCode == Activity.RESULT_OK,
                )
                running = false
            }

            fun execute(requestAttempted: Boolean) {
                scope.launch {
                    running = true
                    val updated = probe.run(requestAttempted, externalToken)
                    probeState.updateRun(updated)
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
                        Text("External-helper fixture, bounded keyset pages, and cleanup consent.")
                        Text(
                            if (ExternalFixtureContract.isValidToken(externalToken)) {
                                "Fixture token: $externalToken"
                            } else {
                                "Open S02 from the external fixture helper."
                            },
                        )
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
                            enabled = !running &&
                                ExternalFixtureContract.isValidToken(externalToken),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (running) "Running..." else "Run S02 probe") }
                        Text(probeState.run?.result?.toJson() ?: "PENDING")
                    }
                }
            }
        }
    }
}
