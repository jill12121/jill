package com.galleryorganizer.spikes.s01

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest

enum class RootKind { DOWNLOAD, PICTURES, OTHER }
enum class DownloadRootSelection { TRUE, FALSE, PROMPT_MESSAGE }
enum class S01SingleDeviceOutcome { PASS, PARTIAL, FAIL }

data class S01ProbeResult(
    val deviceModel: String,
    val androidSdk: Int,
    val manufacturer: String,
    val securityPatch: String,
    val buildFingerprintHash: String,
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
                !renameSucceeded || !finalReadback || !cleanupSucceeded ||
                !collisionEvidenceComplete(collideCreate) ||
                !collisionEvidenceComplete(collideRename) ||
                !collisionEvidenceComplete(createDirIdempotent) ->
                S01SingleDeviceOutcome.FAIL
            !forceStopRetryPassed || !deviceRebootRetryPassed ||
                !reinstallAdoptionRead || !reinstallAdoptionWrite ||
                !reinstallAdoptionDelete -> S01SingleDeviceOutcome.PARTIAL
            else -> S01SingleDeviceOutcome.PASS
        }

    private fun collisionEvidenceComplete(value: String): Boolean =
        value.isNotBlank() && value != "NOT_RUN" && !value.startsWith("ERROR_")

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
        "manufacturer" to manufacturer, "securityPatch" to securityPatch,
        "buildFingerprintHash" to buildFingerprintHash, "rootKind" to rootKind,
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
        deviceModel, androidSdk, manufacturer, securityPatch, buildFingerprintHash,
        rootKind, canSelectDownloadRoot,
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
        fun redactFingerprint(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            return "sha256:$hex"
        }

        fun decode(value: String?): S01ProbeResult? = runCatching {
            val v = value?.split('|')?.map {
                URLDecoder.decode(it, Charsets.UTF_8.name())
            } ?: return null
            require(v.size == 35)
            S01ProbeResult(
                v[0], v[1].toInt(), v[2], v[3], v[4], RootKind.valueOf(v[5]),
                DownloadRootSelection.valueOf(v[6]), v[7].toBoolean(), v[8].toBoolean(),
                v[9].toBoolean(), v[10], v[11], v[12].toBoolean(), v[13].toBoolean(),
                v[14], v[15], v[16], v[17].toBoolean(), v[18].toBoolean(),
                v[19].toBoolean(), v[20].toBoolean(), v[21].toBoolean(),
                v[22].toBoolean(), v[23], v[24].toDouble(), v[25].toDouble(),
                v[26].toDouble(), v[27].toBoolean(), v[28].toLong(), v[29].toLong(),
                v[30].toLongOrNull(), v[31].split(',').filter(String::isNotEmpty),
                v[32].split(',').filter(String::isNotEmpty), v[33].ifEmpty { null },
                v[34].ifEmpty { null },
            )
        }.getOrNull()

        private fun jsonValue(value: Any?): String = when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { jsonValue(it) }
            else -> "\"${escapeJsonString(value.toString())}\""
        }

        private fun escapeJsonString(value: String): String = buildString {
            value.forEach { c ->
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
                }
            }
        }
    }
}
