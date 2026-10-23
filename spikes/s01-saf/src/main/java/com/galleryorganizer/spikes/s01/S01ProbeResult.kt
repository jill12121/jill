package com.galleryorganizer.spikes.s01

import java.net.URLDecoder
import java.net.URLEncoder

enum class RootKind { DOWNLOAD, PICTURES, OTHER }
enum class DownloadRootSelection { TRUE, FALSE, PROMPT_MESSAGE }
enum class S01SingleDeviceOutcome { PASS, PARTIAL, FAIL }

data class S01ProbeResult(
    val deviceModel: String,
    val androidSdk: Int,
    val buildFingerprint: String,
    val rootKind: RootKind,
    val canSelectDownloadRoot: DownloadRootSelection,
    val organizerRootReady: Boolean,
    val partWriteReadback: Boolean,
    val renameSucceeded: Boolean,
    val renamedActualDisplayName: String,
    val renamedActualMime: String,
    val finalReadback: Boolean,
    val cleanupSucceeded: Boolean,
    val collideCreate: String,
    val collideRename: String,
    val createDirIdempotent: String,
    val oldUriStillReadable: Boolean,
    val forceStopRetryPassed: Boolean = false,
    val deviceRebootRetryPassed: Boolean = false,
    val reinstallAdoptionRead: Boolean = false,
    val reinstallAdoptionWrite: Boolean = false,
    val reinstallAdoptionDelete: Boolean = false,
    val reinstallOrphanClass: String = "NOT_TESTED",
    val writeMbps: Double,
    val readMbps: Double,
    val peakHeapMb: Double,
    val seekSupported: Boolean,
    val enumerate2kMillis: Long,
    val createPerFileMicros: Long,
    val enumerate5kMillis: Long?,
    val flagsRoot: List<String>,
    val flagsFile: List<String>,
    val exceptionClass: String? = null,
    val exceptionMaskedMessage: String? = null,
) {
    val outcome: S01SingleDeviceOutcome
        get() = when {
            exceptionClass != null || !organizerRootReady || !partWriteReadback ||
                !renameSucceeded || !finalReadback || !cleanupSucceeded ->
                S01SingleDeviceOutcome.FAIL
            !forceStopRetryPassed || !deviceRebootRetryPassed ||
                !reinstallAdoptionRead || !reinstallAdoptionWrite ||
                !reinstallAdoptionDelete -> S01SingleDeviceOutcome.PARTIAL
            else -> S01SingleDeviceOutcome.PASS
        }

    fun reportLines(): List<String> = listOf(
        "device=$deviceModel / SDK $androidSdk", "root=$rootKind",
        "downloadRoot=$canSelectDownloadRoot", "partReadback=$partWriteReadback",
        "rename=$renameSucceeded ($renamedActualDisplayName, $renamedActualMime)",
        "finalReadback=$finalReadback", "cleanup=$cleanupSucceeded",
        "forceStopRetry=$forceStopRetryPassed", "rebootRetry=$deviceRebootRetryPassed",
        "reinstall=$reinstallOrphanClass", "writeMbps=${"%.2f".format(writeMbps)}",
        "readMbps=${"%.2f".format(readMbps)}", "seek=$seekSupported",
        "enumerate2kMillis=$enumerate2kMillis", "enumerate5kMillis=$enumerate5kMillis",
        "outcome=$outcome", "exception=${exceptionClass ?: "none"}",
    )

    fun toJson(): String = linkedMapOf<String, Any?>(
        "deviceModel" to deviceModel, "androidSdk" to androidSdk,
        "buildFingerprint" to buildFingerprint, "rootKind" to rootKind,
        "canSelectDownloadRoot" to when (canSelectDownloadRoot) {
            DownloadRootSelection.TRUE -> true
            DownloadRootSelection.FALSE -> false
            DownloadRootSelection.PROMPT_MESSAGE -> "promptMessage"
        },
        "organizerRootReady" to organizerRootReady,
        "partWriteReadback" to partWriteReadback, "renameSucceeded" to renameSucceeded,
        "renamedActualDisplayName" to renamedActualDisplayName,
        "renamedActualMime" to renamedActualMime, "finalReadback" to finalReadback,
        "cleanupSucceeded" to cleanupSucceeded, "collideCreate" to collideCreate,
        "collideRename" to collideRename, "createDirIdempotent" to createDirIdempotent,
        "oldUriStillReadable" to oldUriStillReadable,
        "forceStopRetryPassed" to forceStopRetryPassed,
        "deviceRebootRetryPassed" to deviceRebootRetryPassed,
        "reinstallAdoptionRead" to reinstallAdoptionRead,
        "reinstallAdoptionWrite" to reinstallAdoptionWrite,
        "reinstallAdoptionDelete" to reinstallAdoptionDelete,
        "reinstallOrphanClass" to reinstallOrphanClass, "writeMbps" to writeMbps,
        "readMbps" to readMbps, "peakHeapMb" to peakHeapMb,
        "seekSupported" to seekSupported, "enumerate2kMillis" to enumerate2kMillis,
        "createPerFileMicros" to createPerFileMicros,
        "enumerate5kMillis" to enumerate5kMillis, "flagsRoot" to flagsRoot,
        "flagsFile" to flagsFile, "exceptionClass" to exceptionClass,
        "exceptionMaskedMessage" to exceptionMaskedMessage,
        "decisionSingleDevice" to outcome,
    ).entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"$key\":${jsonValue(value)}"
    }

    fun encode(): String = values().joinToString("|") {
        URLEncoder.encode(it, Charsets.UTF_8.name())
    }

    private fun values() = listOf(
        deviceModel, androidSdk, buildFingerprint, rootKind, canSelectDownloadRoot,
        organizerRootReady, partWriteReadback, renameSucceeded, renamedActualDisplayName,
        renamedActualMime, finalReadback, cleanupSucceeded, collideCreate, collideRename,
        createDirIdempotent, oldUriStillReadable, forceStopRetryPassed,
        deviceRebootRetryPassed, reinstallAdoptionRead, reinstallAdoptionWrite,
        reinstallAdoptionDelete, reinstallOrphanClass, writeMbps, readMbps, peakHeapMb,
        seekSupported, enumerate2kMillis, createPerFileMicros,
        enumerate5kMillis ?: "", flagsRoot.joinToString(","), flagsFile.joinToString(","),
        exceptionClass ?: "", exceptionMaskedMessage ?: "",
    ).map(Any?::toString)

    companion object {
        fun decode(value: String?): S01ProbeResult? = runCatching {
            val v = value?.split('|')?.map {
                URLDecoder.decode(it, Charsets.UTF_8.name())
            } ?: return null
            require(v.size == 33)
            S01ProbeResult(
                v[0], v[1].toInt(), v[2], RootKind.valueOf(v[3]),
                DownloadRootSelection.valueOf(v[4]), v[5].toBoolean(), v[6].toBoolean(),
                v[7].toBoolean(), v[8], v[9], v[10].toBoolean(), v[11].toBoolean(),
                v[12], v[13], v[14], v[15].toBoolean(), v[16].toBoolean(),
                v[17].toBoolean(), v[18].toBoolean(), v[19].toBoolean(),
                v[20].toBoolean(), v[21], v[22].toDouble(), v[23].toDouble(),
                v[24].toDouble(), v[25].toBoolean(), v[26].toLong(), v[27].toLong(),
                v[28].toLongOrNull(), v[29].split(',').filter(String::isNotEmpty),
                v[30].split(',').filter(String::isNotEmpty), v[31].ifEmpty { null },
                v[32].ifEmpty { null },
            )
        }.getOrNull()

        private fun jsonValue(value: Any?): String = when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
            else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")}\""
        }
    }
}
