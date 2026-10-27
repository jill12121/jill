package com.galleryorganizer.domain

/**
 * Filesystem stat for an image observed inside an immutable album directory.
 * Pure data; callers map real provider results into this model.
 */
data class ObservedImageFile(
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
)

/**
 * Compares a decoded manifest against a pure filesystem observation.
 *
 * - COMMITTED manifests must match bidirectionally: every declared file exists
 *   with matching size/mtime bucket and no stray files are present.
 * - IMPORTING manifests describe an in-progress copy: declared files may be
 *   absent, but when present they must match, and no stray files are allowed.
 */
object ManifestVerifier {
    fun verify(
        manifest: AlbumManifest,
        observed: List<ObservedImageFile>,
        mtimeResolutionMillis: Long = 1_000,
    ): List<ManifestIssue> {
        require(mtimeResolutionMillis > 0)
        val issues = mutableListOf<ManifestIssue>()
        val observedByName = linkedMapOf<String, ObservedImageFile>()
        observed.forEach { file ->
            val name = normalize(file.fileName)
            if (observedByName.put(name, file) != null) {
                issues += ManifestIssue(
                    ManifestIssueCode.FILESYSTEM_MISMATCH,
                    "filesystem.observed[$name]",
                    "Observed file name appears more than once.",
                )
            }
        }

        manifest.images.forEach { image ->
            val actual = observedByName[image.fileName]
            when {
                actual == null && manifest.state == AlbumState.COMMITTED ->
                    issues += ManifestIssue(
                        ManifestIssueCode.FILESYSTEM_MISMATCH,
                        "filesystem.images[${image.fileName}]",
                        "Committed manifest file is missing from the filesystem.",
                    )
                actual != null && !matches(image, actual, mtimeResolutionMillis) ->
                    issues += ManifestIssue(
                        ManifestIssueCode.FILESYSTEM_MISMATCH,
                        "filesystem.images[${image.fileName}]",
                        "Observed size or modified time does not match the manifest.",
                    )
            }
        }

        val declared = manifest.images.mapTo(mutableSetOf()) { it.fileName }
        observedByName.keys.forEach { name ->
            if (name !in declared) {
                issues += ManifestIssue(
                    ManifestIssueCode.FILESYSTEM_MISMATCH,
                    "filesystem.observed[$name]",
                    "Observed file is not declared by the manifest.",
                )
            }
        }
        return issues.sortedBy { it.field }.distinctBy { it.field }
    }

    private fun matches(
        image: ManifestImage,
        observed: ObservedImageFile,
        mtimeResolutionMillis: Long,
    ): Boolean = observed.sizeBytes == image.sizeBytes &&
        bucket(observed.modifiedAtMillis, mtimeResolutionMillis) ==
        bucket(image.modifiedAtMillis, mtimeResolutionMillis)

    private fun bucket(millis: Long, resolution: Long): Long =
        millis / resolution * resolution

    private fun normalize(name: String): String = name.replace('\\', '/').trim()
}
