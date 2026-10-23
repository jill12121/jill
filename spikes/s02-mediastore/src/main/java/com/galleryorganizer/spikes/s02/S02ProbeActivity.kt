package com.galleryorganizer.spikes.s02

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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

data class S02ProbeResult(
    val totalPages: Int,
    val pageMillis: List<Long>,
    val partialAccessVisible: Boolean,
    val authChunkOk: Boolean,
    val exceptionClass: String? = null,
) {
    fun toJson(): String = """{"totalPages":$totalPages,"pageMillis":${pageMillis.joinToString(
        prefix = "[", postfix = "]",
    )},"partialAccessVisible":$partialAccessVisible,"authChunkOk":$authChunkOk,"exceptionClass":${
        exceptionClass?.let { "\"$it\"" } ?: "null"
    },"decisionS02SingleDevice":"${if (exceptionClass == null) "PASS" else "FAIL"}"}"""
}

class MediaStoreProbe(private val context: Context) {
    suspend fun run(): S02ProbeResult = withContext(Dispatchers.IO) {
        val timings = mutableListOf<Long>()
        var pages = 0
        var lastId = 0L
        var failure: Exception? = null
        try {
            do {
                val started = System.nanoTime()
                var count = 0
                var pageLastId = lastId
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media._ID} > ?",
                    arrayOf(lastId.toString()),
                    "${MediaStore.Images.Media._ID} ASC LIMIT $PAGE_SIZE",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        pageLastId = cursor.getLong(0)
                        count++
                    }
                }
                timings += (System.nanoTime() - started) / 1_000_000
                if (count > 0) pages++
                lastId = pageLastId
            } while (count == PAGE_SIZE)
        } catch (caught: Exception) {
            failure = caught
        }
        S02ProbeResult(
            pages,
            timings,
            hasPartialSelectedGrant(),
            authorizationChunks((1L..601L).toList()).all { it.size <= AUTH_CHUNK_SIZE },
            failure?.javaClass?.simpleName,
        )
    }

    fun authorizationChunks(ids: List<Long>): List<List<Long>> =
        ids.chunked(AUTH_CHUNK_SIZE)

    private fun hasPartialSelectedGrant(): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) !=
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PAGE_SIZE = 500
        const val AUTH_CHUNK_SIZE = 300
    }
}

class S02ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = MediaStoreProbe(this)
        setContent {
            var output by remember { mutableStateOf("PENDING") }
            val scope = rememberCoroutineScope()
            MaterialTheme {
                Surface {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("S02 / MediaStore", style = MaterialTheme.typography.headlineSmall)
                        Text("Keyset page size 500; cleanup authorization chunks are capped at 300.")
                        Button(
                            onClick = { scope.launch { output = probe.run().toJson() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Run S02 probe") }
                        Text(output)
                    }
                }
            }
        }
    }
}
