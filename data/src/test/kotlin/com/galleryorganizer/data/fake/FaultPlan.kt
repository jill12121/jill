package com.galleryorganizer.data.fake

enum class FaultMode { THROW, PARTIAL_WRITE, SIMULATED_PROCESS_KILL }

data class FaultPlan(val triggerAtOperation: Int, val mode: FaultMode) {
    init { require(triggerAtOperation > 0) }
}

enum class FakeOperationOutcome { COMPLETED, THREW, PARTIAL_WRITE, PROCESS_KILLED }

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
        if (processKilled) throw SimulatedProcessKillException(name)
        val index = ++operationIndex
        when (plan?.takeIf { it.triggerAtOperation == index }?.mode) {
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
            null -> {
                val result = operation()
                log.add(FakeOperationRecord(index, name, FakeOperationOutcome.COMPLETED))
                return result
            }
        }
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
