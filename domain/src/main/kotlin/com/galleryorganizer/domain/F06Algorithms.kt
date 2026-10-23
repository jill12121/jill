package com.galleryorganizer.domain

import java.text.Normalizer
import java.util.Locale

data class SourceEntry(val sourceId: String, val relativePath: String)
data class SourceSubgroup(val name: String, val sourceIds: List<String>)
data class SourceBatchGroup(val batch: String, val subgroups: List<SourceSubgroup>)

object BatchGrouper {
    const val LOOSE_BATCH = "__LOOSE__"
    private const val LOOSE_SUBGROUP = "__ROOT__"

    fun group(entries: List<SourceEntry>): List<SourceBatchGroup> =
        entries.groupBy {
            parts(it.relativePath).let { path -> if (path.size > 1) path.first() else LOOSE_BATCH }
        }
            .map { (batch, batchEntries) ->
                val subgroups = batchEntries.groupBy { entry ->
                    val path = parts(entry.relativePath)
                    if (batch == LOOSE_BATCH || path.size < 3) LOOSE_SUBGROUP else path[1]
                }.map { (name, values) ->
                    SourceSubgroup(name, values.map { it.sourceId }.sorted())
                }.sortedBy { it.name }
                SourceBatchGroup(batch, subgroups)
            }.sortedWith(compareBy<SourceBatchGroup> { it.batch == LOOSE_BATCH }.thenBy { it.batch })

    private fun parts(path: String): List<String> =
        path.replace('\\', '/').split('/').filter { it.isNotBlank() }
}

object SourceKeyNormalizer {
    fun normalize(
        kind: String,
        root: String,
        path: String,
        size: Long,
        modifiedAtMillis: Long,
        mtimeResolutionMillis: Long = 1_000,
    ): String {
        require(size >= 0 && mtimeResolutionMillis > 0)
        val segments = path.replace('\\', '/').split('/').fold(mutableListOf<String>()) { result, part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                else -> result += part
            }
            result
        }
        val normalizedMtime = modifiedAtMillis / mtimeResolutionMillis * mtimeResolutionMillis
        return "${kind.uppercase(Locale.ROOT)}:$root:${segments.joinToString("/")}:$size:$normalizedMtime"
    }
}

object FileNameSanitizer {
    private val reserved = Regex("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$")
    private val invalid = Regex("""[<>:"/\\|?*\u0000-\u001F]""")

    fun sanitize(input: String, maxUtf8Bytes: Int = 255): String {
        require(maxUtf8Bytes > 0)
        var normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
            .replace(invalid, "").trim().trimEnd('.', ' ')
        if (normalized.isEmpty()) normalized = "_"
        if (reserved.matches(normalized)) normalized = "_$normalized"
        if (normalized.encodeToByteArray().size <= maxUtf8Bytes) return normalized
        val dot = normalized.lastIndexOf('.')
        val extension = if (dot > 0) normalized.substring(dot) else ""
        val extensionBytes = extension.encodeToByteArray().size
        val suffix = if (extensionBytes < maxUtf8Bytes) extension else ""
        return truncateUtf8(normalized.removeSuffix(suffix), maxUtf8Bytes - suffix.encodeToByteArray().size) + suffix
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        val result = StringBuilder()
        var used = 0
        value.codePoints().forEach { codePoint ->
            val text = String(Character.toChars(codePoint))
            val bytes = text.encodeToByteArray().size
            if (used + bytes <= maxBytes) {
                result.append(text)
                used += bytes
            }
        }
        return result.toString()
    }
}

object ShortImageIdCollisionRetry {
    fun choose(fullId: String, used: Set<String>, minimumLength: Int = 6): String {
        require(fullId.length >= minimumLength)
        return (minimumLength..fullId.length)
            .map(fullId::take)
            .firstOrNull { it !in used }
            ?: error("Full image id collides")
    }
}

data class MethodFingerprint(val fpMethod: String, val value: String)

object FingerprintComparator {
    fun same(left: MethodFingerprint, right: MethodFingerprint): Boolean =
        left.fpMethod == right.fpMethod && left.value == right.value
}

object PlanBuilder {
    fun build(
        planId: String,
        type: PlanType,
        snapshot: PlanSnapshot,
        createdAtEpochMillis: Long,
        payload: PlanPayload,
        steps: List<Pair<PlanStepType, String>>,
    ): OrganizerPlan = OrganizerPlan(
        planId, type, snapshot, createdAtEpochMillis, payload,
        steps.mapIndexed { index, (stepType, targetId) -> PlanStep(index, stepType, targetId) },
    )
}

object PlanValidator {
    fun validate(plan: OrganizerPlan): List<PlanSchemaIssue> = PlanSchemas.validate(plan)
}

data class SelectionCandidate(val sourceKey: String, val displayName: String)
data class SelectionSnapshot(
    val scanGeneration: Long,
    val draftRevision: Long,
    val items: List<SelectionCandidate>,
)

object SelectionSnapshotMaterializer {
    fun materialize(
        scanGeneration: Long,
        draftRevision: Long,
        candidates: Collection<SelectionCandidate>,
        selectedKeys: Set<String>,
    ): SelectionSnapshot {
        require(scanGeneration > 0 && draftRevision > 0)
        val byKey = candidates.associateBy { it.sourceKey }
        require(selectedKeys.all(byKey::containsKey))
        return SelectionSnapshot(
            scanGeneration,
            draftRevision,
            selectedKeys.sorted().map { byKey.getValue(it).copy() },
        )
    }
}
