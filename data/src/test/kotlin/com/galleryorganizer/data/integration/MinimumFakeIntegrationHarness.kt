package com.galleryorganizer.data.integration

import com.galleryorganizer.contract.*
import com.galleryorganizer.data.fake.*
import com.galleryorganizer.data.room.OrganizerMetadataDao
import com.galleryorganizer.data.room.OrganizerMetadataDatabase
import com.galleryorganizer.data.room.SourceDecisionEntity

enum class OccupiedState { PART, FINAL, TRASH }
enum class InMemoryJournalState { PLANNED, EXECUTING, COMPLETED }
enum class InMemoryStepState { PLANNED, EXECUTING, COMPLETED }

data class JournalStepSnapshot(
    val index: Int,
    val type: String,
    val state: InMemoryStepState,
)

data class JournalSnapshot(
    val operationId: OperationId,
    val state: InMemoryJournalState,
    val watermark: Long,
    val steps: List<JournalStepSnapshot>,
    val diskJournalCreated: Boolean = false,
) {
    val completedSteps: List<JournalStepSnapshot>
        get() = steps.filter { it.state == InMemoryStepState.COMPLETED }
}

data class RecoverySnapshot(
    val operationId: OperationId,
    val watermark: Long,
    val completedSteps: List<JournalStepSnapshot>,
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
    private data class MutableStep(
        val index: Int,
        val type: String,
        var state: InMemoryStepState = InMemoryStepState.PLANNED,
    )

    private data class State(
        var operationState: InMemoryJournalState = InMemoryJournalState.PLANNED,
        var watermark: Long = 0,
        val steps: MutableList<MutableStep>,
    )

    private val states = mutableMapOf<OperationId, State>()

    suspend fun start(operationId: OperationId, stepTypes: List<String>) {
        require(operationId !in states) { "Operation already exists." }
        require(stepTypes.isNotEmpty()) { "At least one step is required." }
        faultInjector.execute("journal.start") {
            states[operationId] = State(
                steps = stepTypes.mapIndexed { index, type ->
                    MutableStep(index, type)
                }.toMutableList(),
            )
        }
    }

    suspend fun completeStep(operationId: OperationId, stepIndex: Int, type: String) {
        val operation = requireNotNull(states[operationId]) { "Operation does not exist." }
        require(operation.operationState != InMemoryJournalState.COMPLETED) {
            "Completed operation cannot accept steps."
        }
        val step = requireNotNull(operation.steps.getOrNull(stepIndex)) { "Step is not planned." }
        require(step.type == type) { "Step type does not match the plan." }
        require(step.state == InMemoryStepState.PLANNED) { "Step is already started." }
        require(operation.steps.take(stepIndex).all { it.state == InMemoryStepState.COMPLETED }) {
            "Previous steps must complete first."
        }
        operation.operationState = InMemoryJournalState.EXECUTING
        step.state = InMemoryStepState.EXECUTING
        faultInjector.execute("journal.completeStep") {
            step.state = InMemoryStepState.COMPLETED
            operation.watermark = operation.steps
                .takeWhile { it.state == InMemoryStepState.COMPLETED }
                .size
                .toLong()
        }
    }

    suspend fun finish(operationId: OperationId) {
        val operation = requireNotNull(states[operationId]) { "Operation does not exist." }
        require(operation.operationState != InMemoryJournalState.COMPLETED) {
            "Operation is already completed."
        }
        require(operation.steps.all { it.state == InMemoryStepState.COMPLETED }) {
            "Every planned step must complete first."
        }
        faultInjector.execute("journal.finish") {
            operation.operationState = InMemoryJournalState.COMPLETED
        }
    }

    fun snapshot(operationId: OperationId): JournalSnapshot {
        val state = requireNotNull(states[operationId]) { "Operation does not exist." }
        return JournalSnapshot(
            operationId,
            state.operationState,
            state.watermark,
            state.steps.map { JournalStepSnapshot(it.index, it.type, it.state) },
        )
    }
}

fun sourceDecisionKey(
    kind: String,
    rootId: String,
    relativePath: String,
    sizeBytes: Long,
    modifiedAtNormalized: Long,
): String = listOf(kind, rootId, relativePath, sizeBytes, modifiedAtNormalized).joinToString(":")

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
