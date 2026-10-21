package com.galleryorganizer.data.integration

import com.galleryorganizer.contract.*
import com.galleryorganizer.data.fake.*
import com.galleryorganizer.data.room.OrganizerMetadataDao
import com.galleryorganizer.data.room.OrganizerMetadataDatabase
import com.galleryorganizer.data.room.SourceDecisionEntity

enum class OccupiedState { PART, FINAL, TRASH }
enum class InMemoryJournalState { PLANNED, EXECUTING }

data class CompletedJournalStep(val index: Int, val type: String)

data class JournalSnapshot(
    val operationId: OperationId,
    val state: InMemoryJournalState,
    val watermark: Long,
    val completedSteps: List<CompletedJournalStep>,
    val diskJournalCreated: Boolean = false,
)

data class RecoverySnapshot(
    val operationId: OperationId,
    val watermark: Long,
    val completedSteps: List<CompletedJournalStep>,
    val partPresent: Boolean,
    val finalPresent: Boolean,
    val manifestState: FakeManifestState,
    val diskJournalCreated: Boolean,
    val diskManifestCreated: Boolean = false,
)

class PartialScanSnapshot(initiallyVisible: Set<SourceFileRef>) {
    private val mutableVisibleRefs = initiallyVisible.toMutableSet()
    val visibleRefs: Set<SourceFileRef> get() = mutableVisibleRefs.toSet()
    var completed: Boolean = false
        private set

    fun observe(result: FsResult<SourceBatch>) {
        if (result is FsResult.Success) {
            mutableVisibleRefs += result.value.files.map(SourceFile::ref)
            completed = result.value.nextCursor == null
        }
    }
}

class InMemoryJournalHarness(
    private val faultInjector: FaultInjector = FaultInjector(),
) {
    private data class State(
        var operationState: InMemoryJournalState = InMemoryJournalState.PLANNED,
        var watermark: Long = 0,
        val completedSteps: MutableList<CompletedJournalStep> = mutableListOf(),
    )

    private val states = mutableMapOf<OperationId, State>()

    suspend fun start(operationId: OperationId) {
        require(operationId !in states) { "Operation already exists." }
        faultInjector.execute("journal.start") {
            states[operationId] = State()
        }
    }

    suspend fun completeStep(operationId: OperationId, stepIndex: Int, type: String) {
        val state = requireNotNull(states[operationId]) { "Operation does not exist." }
        require(stepIndex == state.completedSteps.size) { "Step must be unique and contiguous." }
        faultInjector.execute("journal.completeStep") {
            state.completedSteps += CompletedJournalStep(stepIndex, type)
            state.operationState = InMemoryJournalState.EXECUTING
            state.watermark = stepIndex.toLong() + 1
        }
    }

    fun snapshot(operationId: OperationId): JournalSnapshot {
        val state = requireNotNull(states[operationId]) { "Operation does not exist." }
        return JournalSnapshot(
            operationId,
            state.operationState,
            state.watermark,
            state.completedSteps.toList(),
        )
    }
}

class MinimumFakeIntegrationHarness(
    private val database: OrganizerMetadataDatabase,
    private val dao: OrganizerMetadataDao,
) {
    fun findSourceDecision(sourceKey: String): SourceDecisionEntity? =
        database.openHelper.readableDatabase.query(
            "SELECT sourceKey, decision, updatedAtEpochMillis FROM source_decisions WHERE sourceKey = ?",
            arrayOf(sourceKey),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            SourceDecisionEntity(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getLong(2),
            )
        }

    fun importedLinkageIds(): Set<String> =
        database.openHelper.readableDatabase.query(
            "SELECT sourceFileId, targetFileId FROM import_records WHERE state = 'COMMITTED'",
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                    add(cursor.getString(1))
                }
            }
        }

    fun isOrdinaryDuplicateCandidate(fileId: String): Boolean =
        dao.findFileFact(fileId) != null && fileId !in importedLinkageIds()

    suspend fun writePart(
        workspace: FakeWorkspaceGateway,
        path: RelativePath,
        bytes: ByteArray,
    ): PartInfo = (workspace.writePart(
        path,
        FakeByteSource(bytes),
        FakeDigestSink(Sha256("digest-${path.value}")),
    ) as FsResult.Success).value

    suspend fun occupy(
        workspace: FakeWorkspaceGateway,
        target: RelativePath,
        state: OccupiedState,
        bytes: ByteArray,
    ) {
        when (state) {
            OccupiedState.PART -> writePart(workspace, target, bytes)
            OccupiedState.FINAL -> {
                val part = writePart(workspace, RelativePath("${target.value}.seed.part"), bytes)
                workspace.promotePart(part, target)
            }
            OccupiedState.TRASH -> {
                val part = writePart(workspace, RelativePath("${target.value}.seed.part"), bytes)
                val final = (workspace.promotePart(
                    part,
                    RelativePath("${target.value}.seed.final"),
                ) as FsResult.Success).value
                workspace.moveToTrash(final, target)
            }
        }
    }

    fun occupiedBytes(
        workspace: FakeWorkspaceGateway,
        target: RelativePath,
        state: OccupiedState,
    ): ByteArray? = when (state) {
        OccupiedState.PART -> workspace.partBytes(target)
        OccupiedState.FINAL -> workspace.finalBytes(target)
        OccupiedState.TRASH -> workspace.trashBytes(target)
    }
}

fun recoverySnapshot(
    operationId: OperationId,
    journal: InMemoryJournalHarness,
    workspace: FakeWorkspaceGateway,
    manifest: FakeManifestStore,
    partPath: RelativePath,
): RecoverySnapshot {
    val journalSnapshot = journal.snapshot(operationId)
    return RecoverySnapshot(
        operationId = operationId,
        watermark = journalSnapshot.watermark,
        completedSteps = journalSnapshot.completedSteps,
        partPresent = workspace.partBytes(partPath) != null,
        finalPresent = workspace.finalBytes(RelativePath(partPath.value.removeSuffix(".part"))) != null,
        manifestState = manifest.state(operationId),
        diskJournalCreated = journalSnapshot.diskJournalCreated,
    )
}
