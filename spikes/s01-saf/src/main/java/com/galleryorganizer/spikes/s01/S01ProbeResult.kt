package com.galleryorganizer.spikes.s01

enum class RootKind { DOWNLOAD, PICTURES, OTHER }

enum class S01Decision { GO, LIMITED_GO, NO_GO, PENDING }

data class S01ProbeResult(
    val rootKind: RootKind,
    val permissionPersisted: Boolean,
    val organizerRootReady: Boolean,
    val partWriteReadback: Boolean,
    val renameSucceeded: Boolean,
    val finalReadback: Boolean,
    val cleanupSucceeded: Boolean,
    val savedGrantRestored: Boolean = false,
    val error: String? = null,
) {
    val decision: S01Decision
        get() = when {
            error != null || !permissionPersisted || !organizerRootReady ||
                !partWriteReadback || !cleanupSucceeded -> S01Decision.NO_GO
            rootKind == RootKind.DOWNLOAD && renameSucceeded && finalReadback &&
                savedGrantRestored -> S01Decision.GO
            rootKind != RootKind.OTHER && partWriteReadback ->
                S01Decision.LIMITED_GO
            else -> S01Decision.NO_GO
        }

    fun reportLines(sdk: Int): List<String> = listOf(
        "sdk=$sdk",
        "root=$rootKind",
        "persisted=$permissionPersisted",
        "organizerRoot=$organizerRootReady",
        "partReadback=$partWriteReadback",
        "rename=$renameSucceeded",
        "finalReadback=$finalReadback",
        "cleanup=$cleanupSucceeded",
        "restored=$savedGrantRestored",
        "decision=$decision",
        "error=${error ?: "none"}",
    )

    fun encode(): String {
        val flags = listOf(
            permissionPersisted, organizerRootReady, partWriteReadback,
            renameSucceeded, finalReadback, cleanupSucceeded, savedGrantRestored,
        ).foldIndexed(0) { index, mask, enabled ->
            if (enabled) mask or (1 shl index) else mask
        }
        return "$rootKind|$flags|${error.orEmpty()}"
    }

    companion object {
        fun decode(value: String?): S01ProbeResult? {
            val fields = value?.split('|') ?: return null
            if (fields.size != 3) return null
            return runCatching {
                val flags = fields[1].toInt()
                S01ProbeResult(
                    rootKind = RootKind.valueOf(fields[0]),
                    permissionPersisted = flags and 1 != 0,
                    organizerRootReady = flags and 2 != 0,
                    partWriteReadback = flags and 4 != 0,
                    renameSucceeded = flags and 8 != 0,
                    finalReadback = flags and 16 != 0,
                    cleanupSucceeded = flags and 32 != 0,
                    savedGrantRestored = flags and 64 != 0,
                    error = fields[2].ifEmpty { null },
                )
            }.getOrNull()
        }
    }
}
