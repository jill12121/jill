package com.galleryorganizer.spikes.s04

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
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
import org.json.JSONObject
import java.util.UUID

enum class AtomicInterruption {
    BEFORE_PROCESS_KILL, AFTER_PROCESS_KILL, BEFORE_RENAME, AFTER_RENAME,
}

data class S04ProbeResult(
    val atomicStageSurvivors: Map<AtomicInterruption, Int>,
    val provOverwriteBehavior: String,
    val exceptionClass: String? = null,
) {
    fun toJson(): String {
        val stages = atomicStageSurvivors.entries.joinToString(
            prefix = "{", postfix = "}",
        ) { "\"${it.key}\":${it.value}" }
        return """{"atomicStageSurvivors":$stages,"provOverwriteBehavior":"$provOverwriteBehavior","exceptionClass":${
            exceptionClass?.let { "\"$it\"" } ?: "null"
        },"decisionS04SingleDevice":"${
            if (exceptionClass == null && atomicStageSurvivors.values.all { it >= 1 }) "PASS"
            else "FAIL"
        }"}"""
    }
}

class ManifestAtomicProbe(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun run(tree: Uri): S04ProbeResult = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        val survivors = linkedMapOf<AtomicInterruption, Int>()
        var overwrite = "NOT_RUN"
        var failure: Exception? = null
        try {
            AtomicInterruption.entries.forEach { stage ->
                survivors[stage] = runStage(parent, stage)
            }
            overwrite = overwriteProbe(parent)
        } catch (caught: Exception) {
            failure = caught
        }
        S04ProbeResult(survivors, overwrite, failure?.javaClass?.simpleName)
    }

    private fun runStage(parent: Uri, stage: AtomicInterruption): Int {
        val token = UUID.randomUUID().toString().take(8)
        val finalName = ".s04-$token.json"
        val tmpName = "$finalName.tmp"
        val bakName = "$finalName.bak"
        val created = mutableListOf<Uri>()
        try {
            var final = create(parent, finalName, """{"generation":1}""").also(created::add)
            val tmp = create(parent, tmpName, """{"generation":2}""").also(created::add)
            if (stage == AtomicInterruption.BEFORE_PROCESS_KILL) {
                return parseableCount(final, tmp)
            }
            val bak = requireNotNull(
                DocumentsContract.renameDocument(resolver, final, bakName),
            ).also(created::add)
            if (stage == AtomicInterruption.AFTER_PROCESS_KILL ||
                stage == AtomicInterruption.BEFORE_RENAME
            ) {
                return parseableCount(bak, tmp)
            }
            val promoted = requireNotNull(
                DocumentsContract.renameDocument(resolver, tmp, finalName),
            ).also(created::add)
            return parseableCount(bak, promoted)
        } finally {
            created.asReversed().distinct().forEach {
                runCatching { DocumentsContract.deleteDocument(resolver, it) }
            }
        }
    }

    private fun overwriteProbe(parent: Uri): String {
        val token = UUID.randomUUID().toString().take(8)
        val name = ".s04-overwrite-$token.json"
        val created = mutableListOf<Uri>()
        return try {
            val existing = create(parent, name, """{"generation":1}""").also(created::add)
            val candidate = create(parent, "$name.tmp", """{"generation":2}""").also(created::add)
            val renamed = DocumentsContract.renameDocument(resolver, candidate, name)
            if (renamed == null) "RETURNED_NULL" else {
                created += renamed
                when {
                    renamed == existing -> "OVERWROTE_EXISTING"
                    displayName(renamed) == name -> "DUPLICATE_EXACT"
                    else -> "AUTO_RENAMED"
                }
            }
        } catch (caught: Exception) {
            "ERROR_${caught.javaClass.simpleName}"
        } finally {
            created.asReversed().distinct().forEach {
                runCatching { DocumentsContract.deleteDocument(resolver, it) }
            }
        }
    }

    private fun create(parent: Uri, name: String, json: String): Uri =
        requireNotNull(
            DocumentsContract.createDocument(resolver, parent, "application/json", name),
        ).also { uri ->
            resolver.openOutputStream(uri, "wt").use {
                requireNotNull(it).write(json.encodeToByteArray())
            }
        }

    private fun parseableCount(vararg uris: Uri): Int = uris.count { uri ->
        runCatching {
            resolver.openInputStream(uri).use { JSONObject(requireNotNull(it).reader().readText()) }
            true
        }.getOrDefault(false)
    }

    private fun displayName(uri: Uri): String = resolver.query(
        uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
    )!!.use { check(it.moveToFirst()); it.getString(0) }
}

class S04ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = ManifestAtomicProbe(this)
        setContent {
            var output by remember { mutableStateOf("PENDING") }
            val scope = rememberCoroutineScope()
            MaterialTheme {
                Surface {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("S04 / Manifest atomicity", style = MaterialTheme.typography.headlineSmall)
                        Text("Uses the organizer root selected in S01; no new root is created.")
                        Button(
                            onClick = {
                                val tree = getSharedPreferences("s01", MODE_PRIVATE)
                                    .getString("treeUri", null)?.let(Uri::parse)
                                if (tree == null) output = "S01_ROOT_REQUIRED"
                                else scope.launch { output = probe.run(tree).toJson() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Run S04 probe") }
                        Text(output)
                    }
                }
            }
        }
    }
}
