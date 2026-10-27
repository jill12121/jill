package com.galleryorganizer.domain

/**
 * Pure value model and strict JSON codec for a single album manifest.
 * F06 uses it for golden fixtures and validation only; it performs no file,
 * Android, or storage I/O.
 */
data class ManifestImage(
    val imageId: String,
    val fileName: String,
    val order: Int,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val fpMethod: String,
    val fpValue: String,
)

data class AlbumManifest(
    val schemaVersion: Int,
    val albumId: String,
    val state: AlbumState,
    val createdAtEpochMillis: Long,
    val committedAtEpochMillis: Long?,
    val images: List<ManifestImage>,
)

enum class ManifestIssueCode {
    MALFORMED_JSON,
    UNSUPPORTED_SCHEMA,
    MISSING_FIELD,
    INVALID_VALUE,
    DUPLICATE_IMAGE_ID,
}

data class ManifestIssue(
    val code: ManifestIssueCode,
    val field: String,
    val message: String,
)

sealed interface ManifestDecodeResult {
    data class Valid(val manifest: AlbumManifest) : ManifestDecodeResult
    data class Invalid(val issues: List<ManifestIssue>) : ManifestDecodeResult
}

object AlbumManifestCodec {
    const val SUPPORTED_SCHEMA_VERSION = 1

    fun decode(json: String): ManifestDecodeResult {
        val root = try {
            MiniJson.parse(json)
        } catch (e: IllegalArgumentException) {
            return malformed()
        }
        if (root !is Map<*, *>) return malformed()

        val issues = mutableListOf<ManifestIssue>()

        val version = when (val raw = root["schemaVersion"]) {
            null -> {
                issues += ManifestIssue(
                    ManifestIssueCode.MISSING_FIELD, "schemaVersion",
                    "Schema version is required.",
                )
                null
            }
            is Long -> raw.toInt()
            else -> {
                issues += ManifestIssue(
                    ManifestIssueCode.UNSUPPORTED_SCHEMA, "schemaVersion",
                    "Schema version must be an integer.",
                )
                null
            }
        }
        if (version != null && version != SUPPORTED_SCHEMA_VERSION) {
            issues += ManifestIssue(
                ManifestIssueCode.UNSUPPORTED_SCHEMA, "schemaVersion",
                "Unsupported schema version: $version.",
            )
        }

        val albumId = root.stringField("albumId", issues)
        val stateName = root.stringField("state", issues)
        val state = stateName?.let { name ->
            enumValues<AlbumState>().firstOrNull {
                it.name == name && it != AlbumState.RECOVERY_REQUIRED
            } ?: run {
                issues += ManifestIssue(
                    ManifestIssueCode.INVALID_VALUE, "state",
                    "Persisted manifest state must be IMPORTING or COMMITTED.",
                )
                null
            }
        }
        val createdAt = root.longField("createdAtEpochMillis", issues)
        if (createdAt != null && createdAt <= 0) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "createdAtEpochMillis",
                "Creation time must be positive.",
            )
        }
        val committedAt = root.nullableLongField("committedAtEpochMillis", issues)

        val images = mutableListOf<ManifestImage>()
        when (val rawImages = root["images"]) {
            null -> issues += ManifestIssue(
                ManifestIssueCode.MISSING_FIELD, "images", "Images list is required.",
            )
            !is List<*> -> issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "images", "Images must be a list.",
            )
            else -> rawImages.forEachIndexed { index, item ->
                parseImage(item, "images[$index]", issues)?.let(images::add)
            }
        }

        val seenIds = mutableSetOf<String>()
        images.forEach { image ->
            val key = image.imageId.lowercase()
            if (!seenIds.add(key)) {
                issues += ManifestIssue(
                    ManifestIssueCode.DUPLICATE_IMAGE_ID,
                    "images[${image.imageId}]",
                    "Image id appears more than once: ${image.imageId}.",
                )
            }
        }

        if (state == AlbumState.IMPORTING && committedAt != null) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "committedAtEpochMillis",
                "Importing manifest must not carry a commit time.",
            )
        }
        if (state == AlbumState.COMMITTED && (committedAt == null || committedAt <= 0)) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "committedAtEpochMillis",
                "Committed manifest requires a positive commit time.",
            )
        }

        return if (issues.isEmpty()) {
            ManifestDecodeResult.Valid(
                AlbumManifest(
                    schemaVersion = version!!,
                    albumId = albumId!!,
                    state = state!!,
                    createdAtEpochMillis = createdAt!!,
                    committedAtEpochMillis = committedAt,
                    images = images,
                ),
            )
        } else {
            ManifestDecodeResult.Invalid(issues.sortedBy { it.field })
        }
    }

    private fun malformed() = ManifestDecodeResult.Invalid(
        listOf(
            ManifestIssue(
                ManifestIssueCode.MALFORMED_JSON, "$",
                "Manifest is not well-formed JSON.",
            ),
        ),
    )

    private fun parseImage(
        item: Any?,
        path: String,
        issues: MutableList<ManifestIssue>,
    ): ManifestImage? {
        if (item !is Map<*, *>) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, path, "Image entry must be an object.",
            )
            return null
        }
        val imageId = item.stringField("imageId", "$path.imageId", issues)
        val fileName = item.stringField("fileName", "$path.fileName", issues)
        val order = item.longField("order", "$path.order", issues)
        val sizeBytes = item.longField("sizeBytes", "$path.sizeBytes", issues)
        val modifiedAt = item.longField(
            "modifiedAtMillis", "$path.modifiedAtMillis", issues,
        )
        val fpMethod = item.stringField("fpMethod", "$path.fpMethod", issues)
        val fpValue = item.stringField("fpValue", "$path.fpValue", issues)

        if (order != null && order < 0) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "$path.order",
                "Order must not be negative.",
            )
        }
        if (sizeBytes != null && sizeBytes < 0) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "$path.sizeBytes",
                "Size must not be negative.",
            )
        }
        if (modifiedAt != null && modifiedAt <= 0) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "$path.modifiedAtMillis",
                "Modified time must be positive.",
            )
        }
        if (imageId != null && fileName != null && !fileNameMatches(fileName, imageId)) {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, "$path.fileName",
                "File name must stay relative and start with the image id.",
            )
        }
        return if (listOf(imageId, fileName, order, sizeBytes, modifiedAt, fpMethod, fpValue)
                .all { it != null }
        ) {
            ManifestImage(
                imageId = imageId!!,
                fileName = fileName!!,
                order = order!!.toInt(),
                sizeBytes = sizeBytes!!,
                modifiedAtMillis = modifiedAt!!,
                fpMethod = fpMethod!!,
                fpValue = fpValue!!,
            )
        } else {
            null
        }
    }

    private fun fileNameMatches(fileName: String, imageId: String): Boolean {
        if (fileName.startsWith('/') || fileName.startsWith('\\')) return false
        val segments = fileName.replace('\\', '/').split('/')
        if (segments.any { it == ".." || it.isEmpty() }) return false
        val base = segments.last()
        return base.startsWith("${imageId}.", ignoreCase = true)
    }

    private fun Map<*, *>.stringField(
        field: String,
        issues: MutableList<ManifestIssue>,
    ): String? = stringField(field, field, issues)

    private fun Map<*, *>.stringField(
        key: String,
        field: String,
        issues: MutableList<ManifestIssue>,
    ): String? = when (val value = this[key]) {
        null -> {
            if (containsKey(key)) {
                issues += ManifestIssue(
                    ManifestIssueCode.INVALID_VALUE, field, "Field must be a string.",
                )
            } else {
                issues += ManifestIssue(
                    ManifestIssueCode.MISSING_FIELD, field, "Field is required.",
                )
            }
            null
        }
        is String -> {
            if (value.isBlank()) {
                issues += ManifestIssue(
                    ManifestIssueCode.INVALID_VALUE, field, "Field must not be blank.",
                )
                null
            } else {
                value
            }
        }
        else -> {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, field, "Field must be a string.",
            )
            null
        }
    }

    private fun Map<*, *>.longField(
        field: String,
        issues: MutableList<ManifestIssue>,
    ): Long? = longField(field, field, issues)

    private fun Map<*, *>.longField(
        key: String,
        field: String,
        issues: MutableList<ManifestIssue>,
    ): Long? = when (val value = this[key]) {
        null -> {
            if (containsKey(key)) {
                issues += ManifestIssue(
                    ManifestIssueCode.INVALID_VALUE, field, "Field must be an integer.",
                )
            } else {
                issues += ManifestIssue(
                    ManifestIssueCode.MISSING_FIELD, field, "Field is required.",
                )
            }
            null
        }
        is Long -> value
        else -> {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, field, "Field must be an integer.",
            )
            null
        }
    }

    private fun Map<*, *>.nullableLongField(
        name: String,
        issues: MutableList<ManifestIssue>,
    ): Long? = when (val value = this[name]) {
        null -> if (containsKey(name)) null else {
            issues += ManifestIssue(
                ManifestIssueCode.MISSING_FIELD, name, "Field is required.",
            )
            null
        }
        is Long -> value
        else -> {
            issues += ManifestIssue(
                ManifestIssueCode.INVALID_VALUE, name, "Field must be an integer or null.",
            )
            null
        }
    }
}
