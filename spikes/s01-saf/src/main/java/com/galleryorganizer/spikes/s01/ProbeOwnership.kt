package com.galleryorganizer.spikes.s01

import java.security.MessageDigest

data class ProbeOwnership private constructor(val token: String) {
    val directoryName: String
        get() = "$DIRECTORY_PREFIX$token"
    val sampleName: String
        get() = ".s01-sample-$token.bin"
    val adoptionDeleteName: String
        get() = ".s01-adoption-delete-$token.bin"
    val sampleBytes: ByteArray
        get() = "s01-owned-sample:$token".encodeToByteArray()
    val markerBytes: ByteArray
        get() = listOf(
            "schema=$SCHEMA",
            "token=$token",
            "sample=$sampleName",
            "checksum=${sha256(sampleBytes)}",
        ).joinToString("\n").encodeToByteArray()

    companion object {
        const val DIRECTORY_PREFIX = ".s01-probe-"
        const val MARKER_NAME = ".s01-owner"
        private const val SCHEMA = "s01-owner-v1"
        private val tokenPattern = Regex("^[0-9a-f]{32}$")

        fun create(token: String): ProbeOwnership {
            require(tokenPattern.matches(token))
            return ProbeOwnership(token)
        }

        fun validate(
            directoryName: String,
            markerBytes: ByteArray,
            sampleName: String,
            sampleBytes: ByteArray,
        ): ProbeOwnership? = runCatching {
            val lines = markerBytes.decodeToString().split('\n')
            require(lines.size == 4)
            val fields = lines.associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0)
                line.substring(0, separator) to line.substring(separator + 1)
            }
            require(fields.size == 4)
            require(fields["schema"] == SCHEMA)
            val ownership = create(requireNotNull(fields["token"]))
            require(directoryName == ownership.directoryName)
            require(sampleName == ownership.sampleName)
            require(fields["sample"] == ownership.sampleName)
            require(fields["checksum"] == sha256(sampleBytes))
            require(sampleBytes.contentEquals(ownership.sampleBytes))
            ownership
        }.getOrNull()

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }
}

object SafProbeRules {
    fun classifyTreeDocumentId(documentId: String): RootKind {
        val path = documentId.substringAfter(':').trimEnd('/').lowercase()
        return when {
            path == "download" || path.startsWith("download/") -> RootKind.DOWNLOAD
            path == "pictures" || path.startsWith("pictures/") -> RootKind.PICTURES
            else -> RootKind.OTHER
        }
    }

    fun downloadRootSelection(documentId: String): DownloadRootSelection {
        val path = documentId.substringAfter(':').trimEnd('/').lowercase()
        return if (path == "download") {
            DownloadRootSelection.TRUE
        } else {
            DownloadRootSelection.PROMPT_MESSAGE
        }
    }
}

class CleanupLedger<T> {
    private val created = mutableListOf<T>()

    fun register(value: T): T = value.also(created::add)

    fun cleanupOrder(): List<T> = created.asReversed().distinct()

    fun cleanup(delete: (T) -> Boolean): Boolean {
        var succeeded = true
        cleanupOrder().forEach { succeeded = delete(it) && succeeded }
        return succeeded
    }
}
