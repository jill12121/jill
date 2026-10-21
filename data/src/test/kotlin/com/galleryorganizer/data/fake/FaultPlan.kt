package com.galleryorganizer.data.fake

import com.galleryorganizer.contract.FsResult
import com.galleryorganizer.contract.StorageFailure

sealed interface FaultMode {
    data object THROW : FaultMode
    data object PARTIAL_WRITE : FaultMode
    data object SIMULATED_PROCESS_KILL : FaultMode
    data class ReturnFailure(val failure: StorageFailure) : FaultMode
}

data class FaultPlan(val triggerAtOperation: Int, val mode: FaultMode) {
    init { require(triggerAtOperation > 0) }
}

enum class FakeOperationOutcome {
    COMPLETED,
    RETURNED_FAILURE,
    THREW,
    PARTIAL_WRITE,
    PROCESS_KILLED,
}

data class FakeOperationRecord(val index: Int, val name: String, val outcome: FakeOperationOutcome)

class FakeOperationLog {
    private val mutableRecords = mutableListOf<FakeOperationRecord>()
    val records: List<FakeOperationRecord> get() = mutableRecords.toList()

    internal fun add(record: FakeOperationRecord) { mutableRecords += record }
}

class InjectedFaultException(operation: String) : RuntimeException("Injected fault at $operation")
class InjectedPartialWriteException(operation: String) : RuntimeException("Injected partial write at $operation")
class SimulatedProcessKillException(operation: String) : RuntimeException("Simulated process kill at $operation")

class FaultInjector(
    private val plan: FaultPlan? = null,
    private val log: FakeOperationLog = FakeOperationLog(),
) {
    private var operationIndex = 0
    private var processKilled = false

    suspend fun <T> execute(
        name: String,
        partialWrite: suspend () -> Unit = {},
        operation: suspend () -> T,
    ): T {
        val (index, mode) = nextOperation(name)
        when (mode) {
            FaultMode.THROW -> fail(index, name, FakeOperationOutcome.THREW) { InjectedFaultException(name) }
            FaultMode.PARTIAL_WRITE -> {
                partialWrite()
                fail(index, name, FakeOperationOutcome.PARTIAL_WRITE) {
                    InjectedPartialWriteException(name)
                }
            }
            FaultMode.SIMULATED_PROCESS_KILL -> {
                processKilled = true
                fail(index, name, FakeOperationOutcome.PROCESS_KILLED) {
                    SimulatedProcessKillException(name)
                }
            }
            is FaultMode.ReturnFailure -> error("ReturnFailure requires executeResult.")
            null -> {
                val result = operation()
                log.add(FakeOperationRecord(index, name, FakeOperationOutcome.COMPLETED))
                return result
            }
        }
    }

    suspend fun <T> executeResult(
        name: String,
        partialWrite: suspend () -> Unit = {},
        returnedFailure: suspend (StorageFailure) -> Unit = {},
        operation: suspend () -> FsResult<T>,
    ): FsResult<T> {
        val (index, mode) = nextOperation(name)
        when (mode) {
            FaultMode.THROW -> fail(index, name, FakeOperationOutcome.THREW) { InjectedFaultException(name) }
            FaultMode.PARTIAL_WRITE -> {
                partialWrite()
                fail(index, name, FakeOperationOutcome.PARTIAL_WRITE) {
                    InjectedPartialWriteException(name)
                }
            }
            FaultMode.SIMULATED_PROCESS_KILL -> {
                processKilled = true
                fail(index, name, FakeOperationOutcome.PROCESS_KILLED) {
                    SimulatedProcessKillException(name)
                }
            }
            is FaultMode.ReturnFailure -> {
                returnedFailure(mode.failure)
                log.add(FakeOperationRecord(index, name, FakeOperationOutcome.RETURNED_FAILURE))
                return FsResult.Failure(mode.failure)
            }
            null -> {
                val result = operation()
                val outcome = if (result is FsResult.Failure) {
                    FakeOperationOutcome.RETURNED_FAILURE
                } else {
                    FakeOperationOutcome.COMPLETED
                }
                log.add(FakeOperationRecord(index, name, outcome))
                return result
            }
        }
    }

    private fun nextOperation(name: String): Pair<Int, FaultMode?> {
        if (processKilled) throw SimulatedProcessKillException(name)
        val index = ++operationIndex
        return index to plan?.takeIf { it.triggerAtOperation == index }?.mode
    }

    private fun fail(
        index: Int,
        name: String,
        outcome: FakeOperationOutcome,
        error: () -> RuntimeException,
    ): Nothing {
        log.add(FakeOperationRecord(index, name, outcome))
        throw error()
    }
}
