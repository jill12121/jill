package com.galleryorganizer.domain

import com.galleryorganizer.contract.SourceFileRef
import com.galleryorganizer.contract.SourceMediaMetadata

enum class SourceBatchKind { DIRECTORY, ROOT_LOOSE_IMAGES }

data class SourceDiscoveryConfig(
    val organizerRootRelativePath: String,
    val protectedDirectoryPaths: Set<String>,
    val monitoredFirstLevelDirectories: Set<String>,
    val includeRootLooseImages: Boolean = false,
    val coverage: ScanCoverage,
    val completed: Boolean,
)

data class DiscoveredSourceItem(
    val mediaId: Long,
    val ref: SourceFileRef,
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val mimeType: String?,
    val subgroupRelativePath: String?,
)

data class DiscoveredSourceBatch(
    val stableKey: String,
    val displayName: String,
    val kind: SourceBatchKind,
    val items: List<DiscoveredSourceItem>,
    val isMonitored: Boolean,
    val canOrganizeWholeBatch: Boolean,
) {
    val imageCount: Int get() = items.size
}

data class SourceDiscoverySnapshot(
    val batches: List<DiscoveredSourceBatch>,
    val coverage: ScanCoverage,
    val canOrganizeWholeBatch: Boolean,
    val canImportExplicitSelection: Boolean,
)

object SourceDiscoveryPolicy {
    fun build(
        media: List<SourceMediaMetadata>,
        config: SourceDiscoveryConfig,
    ): SourceDiscoverySnapshot {
        val status = ScanStatus(config.coverage, config.completed)
        val excluded = (config.protectedDirectoryPaths + config.organizerRootRelativePath)
            .mapNotNull(::normalizePath)
        val selected = config.monitoredFirstLevelDirectories.map { it.lowercase() }.toSet()
        val grouped = linkedMapOf<String, MutableList<DiscoveredSourceItem>>()

        media.asSequence()
            .filter { it.mediaId > 0 && it.displayName.isNotBlank() }
            .filter { it.mimeType?.startsWith("image/", ignoreCase = true) == true }
            .mapNotNull { item -> normalizePath(item.relativePath)?.let { it to item } }
            .filter { (path) -> path == DOWNLOAD_ROOT || path.startsWith("$DOWNLOAD_ROOT/") }
            .filterNot { (path) -> excluded.any { path.isSameOrChildOf(it) } }
            .distinctBy { (_, item) -> item.mediaId }
            .forEach { (path, item) ->
                val belowDownload = path.removePrefix(DOWNLOAD_ROOT).trim('/')
                val segments = belowDownload.split('/').filter(String::isNotBlank)
                val batchName = segments.firstOrNull().orEmpty()
                val subgroup = segments.drop(1).takeIf(List<String>::isNotEmpty)?.joinToString("/")
                grouped.getOrPut(batchName) { mutableListOf() } += DiscoveredSourceItem(
                    mediaId = item.mediaId,
                    ref = item.ref,
                    relativePath = path,
                    displayName = item.displayName,
                    sizeBytes = item.sizeBytes,
                    modifiedAtEpochMillis = item.modifiedAtEpochMillis,
                    mimeType = item.mimeType,
                    subgroupRelativePath = subgroup,
                )
            }

        val batches = grouped.map { (name, items) ->
            val loose = name.isEmpty()
            DiscoveredSourceBatch(
                stableKey = if (loose) ROOT_LOOSE_KEY else "directory:${name.lowercase()}",
                displayName = if (loose) "Loose images" else name,
                kind = if (loose) SourceBatchKind.ROOT_LOOSE_IMAGES else SourceBatchKind.DIRECTORY,
                items = items,
                isMonitored = if (loose) config.includeRootLooseImages else name.lowercase() in selected,
                canOrganizeWholeBatch = status.canOrganizeWholeBatch(),
            )
        }.sortedWith(compareBy({ it.kind == SourceBatchKind.ROOT_LOOSE_IMAGES }, { it.displayName.lowercase() }))

        return SourceDiscoverySnapshot(
            batches = batches,
            coverage = config.coverage,
            canOrganizeWholeBatch = status.canOrganizeWholeBatch(),
            canImportExplicitSelection = status.canImportExplicitSelection(),
        )
    }

    private fun normalizePath(path: String): String? = path.replace('\\', '/')
        .trim()
        .trim('/')
        .takeIf(String::isNotBlank)

    private fun String.isSameOrChildOf(parent: String): Boolean =
        equals(parent, ignoreCase = true) || startsWith("$parent/", ignoreCase = true)

    private const val DOWNLOAD_ROOT = "Download"
    private const val ROOT_LOOSE_KEY = "virtual:download-root-loose-images"
}
