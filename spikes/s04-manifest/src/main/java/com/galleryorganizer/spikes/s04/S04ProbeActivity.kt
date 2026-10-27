package com.galleryorganizer.spikes.s04

import android.content.Context
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class S04ActionResult(
    val message: String,
    val result: S04ProbeResult,
)

private object S04ProcessSession {
    val id: String = UUID.randomUUID().toString()
    val stateMutex = Mutex()
}

class ManifestAtomicProbe(private val context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("s04", Context.MODE_PRIVATE)

    suspend fun prepare(tree: Uri, stage: AtomicInterruption): S04ActionResult =
        withContext(Dispatchers.IO) {
            S04ProcessSession.stateMutex.withLock {
            if (pendingRecordStatus() != PendingRecordStatus.NONE) {
                return@withContext S04ActionResult("PENDING_STAGE_EXISTS", result())
            }
            val identity = runIdentity(tree)
                ?: return@withContext S04ActionResult(
                    "RUN_IDENTITY_MISMATCH",
                    result("RUN_IDENTITY_MISMATCH"),
                )
            val token = UUID.randomUUID().toString().replace("-", "")
            check(
                preferences.edit()
                    .putString(PENDING_TREE, tree.toString())
                    .putString(PENDING_STAGE, stage.name)
                    .putString(PENDING_TOKEN, token)
                    .putString(PENDING_SESSION, S04ProcessSession.id)
                    .putString(PENDING_PHASE, S04PendingPhase.PREPARING.name)
                    .putString(PENDING_RUN_ID, identity.runId)
                    .putString(PENDING_TREE_HASH, identity.treeUriSha256)
                    .remove(PENDING_ROOT_URI)
                    .remove(observationKey(stage))
                    .commit(),
            )
            var root: Uri? = null
            try {
                val parent = documentUri(tree)
                val createdRoot = createDirectory(parent, "$ROOT_PREFIX$token")
                root = createdRoot
                check(
                    preferences.edit()
                        .putString(PENDING_ROOT_URI, createdRoot.toString())
                        .commit(),
                )
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
        }

    suspend fun observeAfterRestart(): S04ActionResult = withContext(Dispatchers.IO) {
        S04ProcessSession.stateMutex.withLock {
        try {
            observeAfterRestartOnIo()
        } catch (_: ChildQueryFailure) {
            S04ActionResult("QUERY_RETRY_REQUIRED", result())
        }
        }
    }

    private fun observeAfterRestartOnIo(): S04ActionResult {
        val pending = when (val read = pendingStage()) {
            PendingRead.None -> return S04ActionResult("NO_PENDING_STAGE", result())
            is PendingRead.Incompatible -> return resolveIncompatiblePending(read)
            is PendingRead.Valid -> read.value
        }
        if (pending.processSession == S04ProcessSession.id) {
            return S04ActionResult("PROCESS_RESTART_REQUIRED", result())
        }
        if (pending.phase == S04PendingPhase.PREPARING) {
            return cleanupIncompletePreparation(pending)
        }
        if (pending.phase == S04PendingPhase.OBSERVED) {
            return resumeObservedCleanup(pending)
        }
        val parent = documentUri(pending.tree)
        val root = findChild(parent, "$ROOT_PREFIX${pending.token}")
            ?: return failPending(pending, "PROBE_ROOT_MISSING")
        if (!isOwnedRoot(root, pending.token)) {
            return failPending(pending, "NOT_ADOPTABLE")
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
            runId = pending.runId,
            treeUriSha256 = pending.treeUriSha256,
        )
        check(
            preferences.edit()
                .putString(observationKey(pending.stage), encode(observation))
                .putString(OVERWRITE, overwrite)
                .putString(PENDING_PHASE, S04PendingPhase.OBSERVED.name)
                .commit(),
        )
        return completeObservedCleanup(pending, root, observation)
    }

    suspend fun resetRun(): S04ActionResult = withContext(Dispatchers.IO) {
        S04ProcessSession.stateMutex.withLock {
        val pendingStatus = pendingRecordStatus()
        if (!RunResetPolicy.canReset(pendingStatus)) {
            return@withContext S04ActionResult("PENDING_STAGE_BLOCKS_RESET", result())
        }
        check(clearRunState(includePending = false))
        S04ActionResult("RUN_RESET", result())
        }
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
            pendingRecordStatus() != PendingRecordStatus.NONE,
            preferences.getString(RUN_ID, null),
            preferences.getString(RUN_TREE_HASH, null),
            readProvenance(),
        )
    }

    private fun cleanupIncompletePreparation(pending: PendingStage): S04ActionResult {
        val persistedRoot = pending.root
        val query = if (persistedRoot == null) {
            findChildren(documentUri(pending.tree), "$ROOT_PREFIX${pending.token}")
        } else {
            ChildQuery(ChildQueryStatus.ABSENT, emptyList())
        }
        val recovery = PreparingRootRecoveryPolicy.choose(
            persistedRootAvailable = persistedRoot != null,
            queryStatus = query.status,
            matchCount = query.matches.size,
        )
        if (recovery == PreparingRootRecovery.CLEAR_PENDING) {
            clearPending()
            return S04ActionResult("INCOMPLETE_PREPARATION_DISCARDED", result())
        }
        if (recovery == PreparingRootRecovery.RETRY_QUERY) {
            return S04ActionResult("QUERY_RETRY_REQUIRED", result())
        }
        if (recovery == PreparingRootRecovery.REFUSE_AMBIGUOUS) {
            return S04ActionResult(
                "INCOMPLETE_PREPARATION_AMBIGUOUS",
                result("AMBIGUOUS_ROOT"),
            )
        }
        val root = persistedRoot ?: query.matches.single()
        val cleaned = runCatching {
            DocumentsContract.deleteDocument(resolver, root)
        }.getOrDefault(false)
        if (cleaned) {
            clearPending()
            return S04ActionResult("INCOMPLETE_PREPARATION_CLEANED", result())
        }
        if (persistedRoot != null) {
            val confirmation = findChildren(
                documentUri(pending.tree),
                "$ROOT_PREFIX${pending.token}",
            )
            when (
                PreparingDeleteConfirmationPolicy.choose(
                    confirmation.status,
                    confirmation.matches.size,
                )
            ) {
                PreparingDeleteConfirmation.COMPLETE -> {
                    clearPending()
                    return S04ActionResult(
                        "INCOMPLETE_PREPARATION_ALREADY_CLEAN",
                        result(),
                    )
                }
                PreparingDeleteConfirmation.RETRY ->
                    return S04ActionResult("CLEANUP_RETRY_REQUIRED", result())
                PreparingDeleteConfirmation.REFUSE_AMBIGUOUS ->
                    return S04ActionResult(
                        "INCOMPLETE_PREPARATION_AMBIGUOUS",
                        result("AMBIGUOUS_ROOT"),
                    )
            }
        }
        return S04ActionResult(
            "CLEANUP_RETRY_REQUIRED",
            result(),
        )
    }

    private fun resolveIncompatiblePending(
        pending: PendingRead.Incompatible,
    ): S04ActionResult {
        val root = pending.root ?: pending.tree?.let { tree ->
            pending.token?.let { token ->
                val query = findChildren(documentUri(tree), "$ROOT_PREFIX$token")
                when (query.status) {
                    ChildQueryStatus.QUERY_FAILED ->
                        return S04ActionResult("QUERY_RETRY_REQUIRED", result())
                    ChildQueryStatus.ABSENT -> null
                    ChildQueryStatus.FOUND -> {
                        if (query.matches.size != 1) {
                            return S04ActionResult(
                                "INCOMPATIBLE_PENDING_AMBIGUOUS",
                                result("AMBIGUOUS_ROOT"),
                            )
                        }
                        query.matches.single()
                    }
                }
            }
        }
        if (root != null) {
            val cleaned = runCatching {
                DocumentsContract.deleteDocument(resolver, root)
            }.getOrDefault(false)
            if (!cleaned) {
                val tree = pending.tree
                    ?: return S04ActionResult("CLEANUP_RETRY_REQUIRED", result())
                val token = pending.token
                    ?: return S04ActionResult("CLEANUP_RETRY_REQUIRED", result())
                val confirmation = findChildren(
                    documentUri(tree),
                    "$ROOT_PREFIX$token",
                )
                when (
                    PreparingDeleteConfirmationPolicy.choose(
                        confirmation.status,
                        confirmation.matches.size,
                    )
                ) {
                    PreparingDeleteConfirmation.COMPLETE -> Unit
                    PreparingDeleteConfirmation.RETRY ->
                        return S04ActionResult("CLEANUP_RETRY_REQUIRED", result())
                    PreparingDeleteConfirmation.REFUSE_AMBIGUOUS ->
                        return S04ActionResult(
                            "INCOMPATIBLE_PENDING_AMBIGUOUS",
                            result("AMBIGUOUS_ROOT"),
                        )
                }
            }
        }
        check(clearRunState(includePending = true))
        return S04ActionResult("INCOMPATIBLE_PENDING_DISCARDED", result())
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
                .remove(PENDING_RUN_ID)
                .remove(PENDING_TREE_HASH)
                .remove(PENDING_ROOT_URI)
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
            runId = pending.runId,
            treeUriSha256 = pending.treeUriSha256,
        )
        preferences.edit()
            .putString(observationKey(pending.stage), encode(observation))
            .remove(PENDING_TREE)
            .remove(PENDING_STAGE)
            .remove(PENDING_TOKEN)
            .remove(PENDING_SESSION)
            .remove(PENDING_PHASE)
            .remove(PENDING_RUN_ID)
            .remove(PENDING_TREE_HASH)
            .remove(PENDING_ROOT_URI)
            .commit()
        return S04ActionResult(reason, result(reason))
    }

    private fun pendingStage(): PendingRead {
        if (pendingRecordStatus() == PendingRecordStatus.NONE) return PendingRead.None
        val tree = preferences.getString(PENDING_TREE, null)
            ?.takeIf(String::isNotBlank)
            ?.let(Uri::parse)
        val token = preferences.getString(PENDING_TOKEN, null)
            ?.takeIf { it.matches(Regex("""[0-9a-f]{32}""")) }
        val root = preferences.getString(PENDING_ROOT_URI, null)
            ?.takeIf(String::isNotBlank)
            ?.let(Uri::parse)
        val valid = runCatching {
            PendingStage(
                requireNotNull(tree),
                AtomicInterruption.valueOf(
                    requireNotNull(preferences.getString(PENDING_STAGE, null)),
                ),
                requireNotNull(token),
                requireNotNull(preferences.getString(PENDING_SESSION, null)),
                S04PendingPhase.valueOf(
                    requireNotNull(preferences.getString(PENDING_PHASE, null)),
                ),
                requireNotNull(preferences.getString(PENDING_RUN_ID, null)),
                requireNotNull(preferences.getString(PENDING_TREE_HASH, null))
                    .also { require(it.matches(Regex("""[0-9a-f]{64}"""))) },
                root,
            )
        }.getOrNull()
        return valid?.let(PendingRead::Valid)
            ?: PendingRead.Incompatible(tree, token, root)
    }

    private fun pendingRecordStatus(): PendingRecordStatus {
        val keys = listOf(
            PENDING_TREE,
            PENDING_STAGE,
            PENDING_TOKEN,
            PENDING_SESSION,
            PENDING_PHASE,
            PENDING_RUN_ID,
            PENDING_TREE_HASH,
        )
        val hasAny = keys.any(preferences::contains) || preferences.contains(PENDING_ROOT_URI)
        val allRequired = keys.all(preferences::contains)
        val valuesValid = if (allRequired) {
            runCatching {
                AtomicInterruption.valueOf(
                    requireNotNull(preferences.getString(PENDING_STAGE, null)),
                )
                S04PendingPhase.valueOf(
                    requireNotNull(preferences.getString(PENDING_PHASE, null)),
                )
                requireNotNull(preferences.getString(PENDING_TOKEN, null))
                    .matches(Regex("""[0-9a-f]{32}"""))
            }.getOrDefault(false)
        } else {
            false
        }
        return PendingRecordPolicy.classify(hasAny, allRequired, valuesValid)
    }

    private fun clearPending(): Boolean = preferences.edit()
        .remove(PENDING_TREE)
        .remove(PENDING_STAGE)
        .remove(PENDING_TOKEN)
        .remove(PENDING_SESSION)
        .remove(PENDING_PHASE)
        .remove(PENDING_RUN_ID)
        .remove(PENDING_TREE_HASH)
        .remove(PENDING_ROOT_URI)
        .commit()

    private fun clearRunState(includePending: Boolean): Boolean {
        val editor = preferences.edit()
            .remove(RUN_ID)
            .remove(RUN_TREE_HASH)
            .remove(PROVENANCE_SCHEMA)
            .remove(PROVENANCE_VERSION)
            .remove(PROVENANCE_MODEL)
            .remove(PROVENANCE_SDK)
            .remove(PROVENANCE_MANUFACTURER)
            .remove(PROVENANCE_PATCH)
            .remove(PROVENANCE_APP_NAME)
            .remove(PROVENANCE_APP_CODE)
            .remove(PROVENANCE_FINGERPRINT)
            .remove(OVERWRITE)
        AtomicInterruption.entries.forEach { editor.remove(observationKey(it)) }
        if (includePending) {
            editor.remove(PENDING_TREE)
                .remove(PENDING_STAGE)
                .remove(PENDING_TOKEN)
                .remove(PENDING_SESSION)
                .remove(PENDING_PHASE)
                .remove(PENDING_RUN_ID)
                .remove(PENDING_TREE_HASH)
                .remove(PENDING_ROOT_URI)
        }
        return editor.commit()
    }

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
        return runCatching {
            create(
                root,
                ProviderOverwriteClassifier.OVERWRITE_NAME,
                """{"generation":1}""",
            )
            val candidate = create(
                root,
                "${ProviderOverwriteClassifier.OVERWRITE_NAME}.tmp",
                """{"generation":2}""",
            )
            val renamed = DocumentsContract.renameDocument(
                resolver,
                candidate,
                ProviderOverwriteClassifier.OVERWRITE_NAME,
            )
                ?: return@runCatching "RETURNED_NULL"
            val exact = findChildren(root, ProviderOverwriteClassifier.OVERWRITE_NAME)
            if (exact.status == ChildQueryStatus.QUERY_FAILED) throw ChildQueryFailure()
            val exactGenerations = exact.matches.mapNotNull(::generation)
            if (exactGenerations.size != exact.matches.size) return@runCatching "INCONCLUSIVE"
            ProviderOverwriteClassifier.classify(
                exactGenerations,
                displayName(renamed),
                generation(renamed),
            )
        }.getOrElse {
            if (it is ChildQueryFailure) throw it
            "ERROR_${it.javaClass.simpleName}"
        }
    }

    private fun generation(uri: Uri): Int? = runCatching {
        JSONObject(read(uri)).getInt("generation")
    }.getOrNull()

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
        val query = findChildren(parent, name)
        if (query.status == ChildQueryStatus.QUERY_FAILED) throw ChildQueryFailure()
        return query.matches.firstOrNull()
    }

    private fun findChildren(parent: Uri, name: String): ChildQuery {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            parent,
            DocumentsContract.getDocumentId(parent),
        )
        val matches = mutableListOf<Uri>()
        val querySucceeded = runCatching {
            val cursor = resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            ) ?: return@runCatching false
            cursor.use {
                while (it.moveToNext()) {
                    if (it.getString(1) == name) {
                        matches += DocumentsContract.buildDocumentUriUsingTree(
                            parent, it.getString(0),
                        )
                    }
                }
            }
            true
        }.getOrDefault(false)
        val matchCount = matches.size.takeIf { querySucceeded }
        return ChildQuery(ChildQueryPolicy.classify(matchCount), matches)
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
        )?.use { it.count } ?: throw ChildQueryFailure()
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
        value.runId,
        value.treeUriSha256,
    ).joinToString("|")

    private fun decode(stage: AtomicInterruption, value: String): StageObservation? =
        runCatching {
            val fields = value.split('|')
            require(fields.size == 6)
            StageObservation(
                stage,
                fields[0].toBooleanStrict(),
                fields[1].toInt(),
                RecoveryCandidate.valueOf(fields[2]),
                fields[3].toBooleanStrict(),
                fields[4],
                fields[5],
            )
        }.getOrNull()

    private fun observationKey(stage: AtomicInterruption) = "observation_${stage.name}"

    private fun runIdentity(tree: Uri): RunIdentity? {
        val treeHash = sha256(tree.toString())
        val currentProvenance = provenance()
        val existingRunId = preferences.getString(RUN_ID, null)
        if (existingRunId == null) {
            val runId = UUID.randomUUID().toString()
            val editor = preferences.edit()
                .putString(RUN_ID, runId)
                .putString(RUN_TREE_HASH, treeHash)
                .putInt(PROVENANCE_SCHEMA, currentProvenance.schemaVersion)
                .putString(PROVENANCE_VERSION, currentProvenance.probeVersion)
                .putString(PROVENANCE_MODEL, currentProvenance.model)
                .putInt(PROVENANCE_SDK, currentProvenance.sdkInt)
                .putString(PROVENANCE_MANUFACTURER, currentProvenance.manufacturer)
                .putString(PROVENANCE_PATCH, currentProvenance.securityPatch)
                .putString(PROVENANCE_APP_NAME, currentProvenance.appVersionName)
                .putLong(PROVENANCE_APP_CODE, currentProvenance.appVersionCode)
                .putString(PROVENANCE_FINGERPRINT, currentProvenance.fingerprintSha256)
                .remove(OVERWRITE)
            AtomicInterruption.entries.forEach { editor.remove(observationKey(it)) }
            check(editor.commit())
            return RunIdentity(runId, treeHash)
        }
        if (
            preferences.getString(RUN_TREE_HASH, null) != treeHash ||
            readProvenance() != currentProvenance
        ) {
            return null
        }
        return RunIdentity(existingRunId, treeHash)
    }

    @Suppress("DEPRECATION")
    private fun provenance(): S04ProbeProvenance {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return S04ProbeProvenance.fromRawFingerprint(
            model = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            appVersionName = packageInfo.versionName ?: "UNKNOWN",
            appVersionCode = packageInfo.longVersionCode,
            rawFingerprint = Build.FINGERPRINT,
        )
    }

    private fun readProvenance(): S04ProbeProvenance? = runCatching {
        S04ProbeProvenance(
            preferences.getInt(PROVENANCE_SCHEMA, -1),
            requireNotNull(preferences.getString(PROVENANCE_VERSION, null)),
            requireNotNull(preferences.getString(PROVENANCE_MODEL, null)),
            preferences.getInt(PROVENANCE_SDK, -1),
            requireNotNull(preferences.getString(PROVENANCE_MANUFACTURER, null)),
            requireNotNull(preferences.getString(PROVENANCE_PATCH, null)),
            requireNotNull(preferences.getString(PROVENANCE_APP_NAME, null)),
            preferences.getLong(PROVENANCE_APP_CODE, -1),
            requireNotNull(preferences.getString(PROVENANCE_FINGERPRINT, null)),
        )
    }.getOrNull()

    private data class PendingStage(
        val tree: Uri,
        val stage: AtomicInterruption,
        val token: String,
        val processSession: String,
        val phase: S04PendingPhase,
        val runId: String,
        val treeUriSha256: String,
        val root: Uri?,
    )

    private sealed interface PendingRead {
        data object None : PendingRead
        data class Valid(val value: PendingStage) : PendingRead
        data class Incompatible(
            val tree: Uri?,
            val token: String?,
            val root: Uri?,
        ) : PendingRead
    }

    private data class ChildQuery(
        val status: ChildQueryStatus,
        val matches: List<Uri>,
    )

    private class ChildQueryFailure : RuntimeException()

    private data class RunIdentity(val runId: String, val treeUriSha256: String)

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
        const val PENDING_RUN_ID = "pendingRunId"
        const val PENDING_TREE_HASH = "pendingTreeHash"
        const val PENDING_ROOT_URI = "pendingRootUri"
        const val RUN_ID = "runId"
        const val RUN_TREE_HASH = "runTreeHash"
        const val PROVENANCE_SCHEMA = "provenanceSchema"
        const val PROVENANCE_VERSION = "provenanceVersion"
        const val PROVENANCE_MODEL = "provenanceModel"
        const val PROVENANCE_SDK = "provenanceSdk"
        const val PROVENANCE_MANUFACTURER = "provenanceManufacturer"
        const val PROVENANCE_PATCH = "provenancePatch"
        const val PROVENANCE_APP_NAME = "provenanceAppName"
        const val PROVENANCE_APP_CODE = "provenanceAppCode"
        const val PROVENANCE_FINGERPRINT = "provenanceFingerprintSha256"
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
                running = true
                scope.launch {
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
                                running = true
                                scope.launch {
                                    val updated = probe.observeAfterRestart()
                                    action = updated.message
                                    result = updated.result
                                    running = false
                                }
                            },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Observe after restart") }
                        Button(
                            onClick = {
                                running = true
                                scope.launch {
                                    val updated = probe.resetRun()
                                    action = updated.message
                                    result = updated.result
                                    running = false
                                }
                            },
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start new run") }
                        Text(action)
                        Text(result.toJson())
                    }
                }
            }
        }
    }
}
