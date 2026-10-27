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

data class S04ActionResult(
    val message: String,
    val result: S04ProbeResult,
)

private object S04ProcessSession {
    val id: String = UUID.randomUUID().toString()
}

class ManifestAtomicProbe(private val context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("s04", Context.MODE_PRIVATE)

    suspend fun prepare(tree: Uri, stage: AtomicInterruption): S04ActionResult =
        withContext(Dispatchers.IO) {
            if (preferences.contains(PENDING_TOKEN)) {
                return@withContext S04ActionResult("PENDING_STAGE_EXISTS", result())
            }
            val token = UUID.randomUUID().toString().replace("-", "")
            check(
                preferences.edit()
                    .putString(PENDING_TREE, tree.toString())
                    .putString(PENDING_STAGE, stage.name)
                    .putString(PENDING_TOKEN, token)
                    .putString(PENDING_SESSION, S04ProcessSession.id)
                    .putString(PENDING_PHASE, S04PendingPhase.PREPARING.name)
                    .remove(observationKey(stage))
                    .commit(),
            )
            var root: Uri? = null
            try {
                val parent = documentUri(tree)
                val createdRoot = createDirectory(parent, "$ROOT_PREFIX$token")
                root = createdRoot
                check(displayName(createdRoot) == "$ROOT_PREFIX$token")
                check(childCount(createdRoot) == 0)
                create(createdRoot, MARKER_NAME, marker(token))
                val final = create(createdRoot, FINAL_NAME, """{"generation":1}""")
                if (stage != AtomicInterruption.BEFORE_PROCESS_KILL) {
                    val tmp = create(createdRoot, TMP_NAME, """{"generation":2}""")
                    if (stage != AtomicInterruption.AFTER_PROCESS_KILL) {
                        requireNotNull(
                            DocumentsContract.renameDocument(resolver, final, BAK_NAME),
                        )
                        if (stage == AtomicInterruption.AFTER_RENAME) {
                            requireNotNull(
                                DocumentsContract.renameDocument(resolver, tmp, FINAL_NAME),
                            )
                        }
                    }
                }
                check(
                    preferences.edit()
                        .putString(PENDING_PHASE, S04PendingPhase.READY.name)
                        .commit(),
                )
            } catch (caught: Exception) {
                val failedRoot = root
                val cleaned = failedRoot == null || runCatching {
                    DocumentsContract.deleteDocument(resolver, failedRoot)
                }.getOrDefault(false)
                if (cleaned) clearPending()
                return@withContext S04ActionResult(
                    "PREPARE_FAILED_${caught.javaClass.simpleName}",
                    result(caught.javaClass.simpleName),
                )
            }
            S04ActionResult(
                "PREPARED_${stage.name}; FORCE_STOP_AND_REOPEN",
                result(),
            )
        }

    suspend fun observeAfterRestart(): S04ActionResult = withContext(Dispatchers.IO) {
        val pending = pendingStage()
            ?: return@withContext S04ActionResult("NO_PENDING_STAGE", result())
        if (pending.processSession == S04ProcessSession.id) {
            return@withContext S04ActionResult("PROCESS_RESTART_REQUIRED", result())
        }
        if (pending.phase == S04PendingPhase.PREPARING) {
            return@withContext cleanupIncompletePreparation(pending)
        }
        if (pending.phase == S04PendingPhase.OBSERVED) {
            return@withContext resumeObservedCleanup(pending)
        }
        val parent = documentUri(pending.tree)
        val root = findChild(parent, "$ROOT_PREFIX${pending.token}")
            ?: return@withContext failPending(pending, "PROBE_ROOT_MISSING")
        if (!isOwnedRoot(root, pending.token)) {
            return@withContext failPending(pending, "NOT_ADOPTABLE")
        }

        val parseable = linkedSetOf<RecoveryCandidate>()
        candidate(root, FINAL_NAME, RecoveryCandidate.FINAL)?.let(parseable::add)
        candidate(root, TMP_NAME, RecoveryCandidate.TMP)?.let(parseable::add)
        candidate(root, BAK_NAME, RecoveryCandidate.BAK)?.let(parseable::add)
        val overwrite = overwriteProbe(root)
        val observation = StageObservation(
            pending.stage,
            processRestartObserved = true,
            parseableCandidateCount = parseable.size,
            recoveryCandidate = RecoverySelector.choose(parseable),
            cleanupSucceeded = false,
        )
        check(
            preferences.edit()
                .putString(observationKey(pending.stage), encode(observation))
                .putString(OVERWRITE, overwrite)
                .putString(PENDING_PHASE, S04PendingPhase.OBSERVED.name)
                .commit(),
        )
        completeObservedCleanup(pending, root, observation)
    }

    fun result(exceptionClass: String? = null): S04ProbeResult {
        val observations = AtomicInterruption.entries.mapNotNull { stage ->
            preferences.getString(observationKey(stage), null)
                ?.let { decode(stage, it) }
                ?.let { stage to it }
        }.toMap()
        return S04ProbeResult(
            observations,
            preferences.getString(OVERWRITE, "NOT_RUN") ?: "NOT_RUN",
            exceptionClass,
            preferences.contains(PENDING_TOKEN),
        )
    }

    private fun cleanupIncompletePreparation(pending: PendingStage): S04ActionResult {
        val parent = documentUri(pending.tree)
        val root = findChild(parent, "$ROOT_PREFIX${pending.token}")
        if (root == null) {
            clearPending()
            return S04ActionResult("INCOMPLETE_PREPARATION_DISCARDED", result())
        }
        if (!isOwnedRoot(root, pending.token)) {
            return S04ActionResult(
                "INCOMPLETE_PREPARATION_NOT_ADOPTABLE",
                result("NOT_ADOPTABLE"),
            )
        }
        val cleaned = runCatching {
            DocumentsContract.deleteDocument(resolver, root)
        }.getOrDefault(false)
        if (cleaned) clearPending()
        return S04ActionResult(
            if (cleaned) "INCOMPLETE_PREPARATION_CLEANED" else "CLEANUP_RETRY_REQUIRED",
            result(),
        )
    }

    private fun resumeObservedCleanup(pending: PendingStage): S04ActionResult {
        val observation = preferences.getString(observationKey(pending.stage), null)
            ?.let { decode(pending.stage, it) }
            ?: return failPending(pending, "OBSERVATION_MISSING")
        val parent = documentUri(pending.tree)
        val root = findChild(parent, "$ROOT_PREFIX${pending.token}")
        if (root == null) {
            return finalizeCleanup(pending, observation)
        }
        if (!isOwnedRoot(root, pending.token)) {
            return failPending(pending, "NOT_ADOPTABLE")
        }
        return completeObservedCleanup(pending, root, observation)
    }

    private fun completeObservedCleanup(
        pending: PendingStage,
        root: Uri,
        observation: StageObservation,
    ): S04ActionResult {
        val cleaned = runCatching {
            DocumentsContract.deleteDocument(resolver, root)
        }.getOrDefault(false)
        return if (cleaned) {
            finalizeCleanup(pending, observation)
        } else {
            S04ActionResult("CLEANUP_RETRY_REQUIRED", result())
        }
    }

    private fun finalizeCleanup(
        pending: PendingStage,
        observation: StageObservation,
    ): S04ActionResult {
        check(
            preferences.edit()
                .putString(
                    observationKey(pending.stage),
                    encode(observation.copy(cleanupSucceeded = true)),
                )
                .remove(PENDING_TREE)
                .remove(PENDING_STAGE)
                .remove(PENDING_TOKEN)
                .remove(PENDING_SESSION)
                .remove(PENDING_PHASE)
                .commit(),
        )
        return S04ActionResult("OBSERVED_${pending.stage.name}", result())
    }

    private fun failPending(pending: PendingStage, reason: String): S04ActionResult {
        val observation = StageObservation(
            pending.stage,
            processRestartObserved = true,
            parseableCandidateCount = 0,
            recoveryCandidate = RecoveryCandidate.NONE,
            cleanupSucceeded = false,
        )
        preferences.edit()
            .putString(observationKey(pending.stage), encode(observation))
            .remove(PENDING_TREE)
            .remove(PENDING_STAGE)
            .remove(PENDING_TOKEN)
            .remove(PENDING_SESSION)
            .remove(PENDING_PHASE)
            .commit()
        return S04ActionResult(reason, result(reason))
    }

    private fun pendingStage(): PendingStage? = runCatching {
        PendingStage(
            Uri.parse(requireNotNull(preferences.getString(PENDING_TREE, null))),
            AtomicInterruption.valueOf(
                requireNotNull(preferences.getString(PENDING_STAGE, null)),
            ),
            requireNotNull(preferences.getString(PENDING_TOKEN, null)),
            requireNotNull(preferences.getString(PENDING_SESSION, null)),
            preferences.getString(PENDING_PHASE, null)
                ?.let(S04PendingPhase::valueOf)
                ?: S04PendingPhase.READY,
        )
    }.getOrNull()

    private fun clearPending(): Boolean = preferences.edit()
        .remove(PENDING_TREE)
        .remove(PENDING_STAGE)
        .remove(PENDING_TOKEN)
        .remove(PENDING_SESSION)
        .remove(PENDING_PHASE)
        .commit()

    private fun isOwnedRoot(root: Uri, token: String): Boolean {
        if (displayName(root) != "$ROOT_PREFIX$token") return false
        val marker = findChild(root, MARKER_NAME) ?: return false
        return runCatching { read(marker) == marker(token) }.getOrDefault(false)
    }

    private fun candidate(
        root: Uri,
        name: String,
        type: RecoveryCandidate,
    ): RecoveryCandidate? {
        val uri = findChild(root, name) ?: return null
        return if (runCatching { JSONObject(read(uri)) }.isSuccess) type else null
    }

    private fun overwriteProbe(root: Uri): String {
        val name = ".s04-overwrite.json"
        return runCatching {
            val existing = create(root, name, """{"generation":1}""")
            val candidate = create(root, "$name.tmp", """{"generation":2}""")
            val renamed = DocumentsContract.renameDocument(resolver, candidate, name)
                ?: return@runCatching "RETURNED_NULL"
            when {
                renamed == existing -> "OVERWROTE_EXISTING"
                displayName(renamed) == name -> "DUPLICATE_EXACT"
                else -> "AUTO_RENAMED"
            }
        }.getOrElse { "ERROR_${it.javaClass.simpleName}" }
    }

    private fun documentUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )

    private fun createDirectory(parent: Uri, name: String): Uri =
        requireNotNull(
            DocumentsContract.createDocument(
                resolver,
                parent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            ),
        )

    private fun create(parent: Uri, name: String, content: String): Uri =
        requireNotNull(
            DocumentsContract.createDocument(resolver, parent, "application/json", name),
        ).also { uri ->
            check(displayName(uri) == name)
            resolver.openOutputStream(uri, "wt").use {
                requireNotNull(it).write(content.encodeToByteArray())
            }
        }

    private fun read(uri: Uri): String =
        resolver.openInputStream(uri).use { requireNotNull(it).reader().readText() }

    private fun findChild(parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) {
                    return DocumentsContract.buildDocumentUriUsingTree(
                        parent,
                        cursor.getString(0),
                    )
                }
            }
        }
        return null
    }

    private fun childCount(parent: Uri): Int {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        return resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
        )?.use { it.count } ?: 0
    }

    private fun displayName(uri: Uri): String = resolver.query(
        uri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
    )!!.use {
        check(it.moveToFirst())
        it.getString(0)
    }

    private fun marker(token: String): String = "schema=s04-owner-v1\ntoken=$token"

    private fun encode(value: StageObservation): String = listOf(
        value.processRestartObserved,
        value.parseableCandidateCount,
        value.recoveryCandidate,
        value.cleanupSucceeded,
    ).joinToString("|")

    private fun decode(stage: AtomicInterruption, value: String): StageObservation? =
        runCatching {
            val fields = value.split('|')
            require(fields.size == 4)
            StageObservation(
                stage,
                fields[0].toBooleanStrict(),
                fields[1].toInt(),
                RecoveryCandidate.valueOf(fields[2]),
                fields[3].toBooleanStrict(),
            )
        }.getOrNull()

    private fun observationKey(stage: AtomicInterruption) = "observation_${stage.name}"

    private data class PendingStage(
        val tree: Uri,
        val stage: AtomicInterruption,
        val token: String,
        val processSession: String,
        val phase: S04PendingPhase,
    )

    private companion object {
        const val ROOT_PREFIX = ".s04-probe-"
        const val MARKER_NAME = ".s04-owner"
        const val FINAL_NAME = "manifest.json"
        const val TMP_NAME = "manifest.json.tmp"
        const val BAK_NAME = "manifest.json.bak"
        const val PENDING_TREE = "pendingTree"
        const val PENDING_STAGE = "pendingStage"
        const val PENDING_TOKEN = "pendingToken"
        const val PENDING_SESSION = "pendingSession"
        const val PENDING_PHASE = "pendingPhase"
        const val OVERWRITE = "overwrite"
    }
}

class S04ProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = ManifestAtomicProbe(this)
        setContent {
            var action by remember { mutableStateOf("PENDING") }
            var result by remember { mutableStateOf(probe.result()) }
            var running by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            fun prepare(stage: AtomicInterruption) {
                val tree = getSharedPreferences("s01", MODE_PRIVATE)
                    .getString("treeUri", null)?.let(Uri::parse)
                if (tree == null) {
                    action = "S01_ROOT_REQUIRED"
                    return
                }
                scope.launch {
                    running = true
                    val updated = probe.prepare(tree, stage)
                    action = updated.message
                    result = updated.result
                    running = false
                }
            }

            MaterialTheme {
                Surface {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("S04 / Manifest atomicity", style = MaterialTheme.typography.headlineSmall)
                        Text("Prepare one cutoff, force-stop the suite, reopen, then observe.")
                        AtomicInterruption.entries.forEach { stage ->
                            Button(
                                onClick = { prepare(stage) },
                                enabled = !running,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Prepare ${stage.name}") }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    running = true
                                    val updated = probe.observeAfterRestart()
                                    action = updated.message
                                    result = updated.result
                                    running = false
                                }
                            },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Observe after restart") }
                        Text(action)
                        Text(result.toJson())
                    }
                }
            }
        }
    }
}
