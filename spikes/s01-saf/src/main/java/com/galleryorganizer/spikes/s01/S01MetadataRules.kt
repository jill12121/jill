package com.galleryorganizer.spikes.s01

enum class S01MetadataClass {
    NOT_RUN,
    EXACT,
    NAME_APPENDED_EXTENSION,
    MIME_MISMATCH,
    NAME_AND_MIME_MISMATCH,
    OTHER_NAME,
}

object S01MetadataRules {
    fun classify(
        expectedName: String,
        expectedMime: String,
        actualName: String,
        actualMime: String,
    ): S01MetadataClass {
        val nameMatches = actualName == expectedName
        val mimeMatches = actualMime.equals(expectedMime, ignoreCase = true)
        return when {
            nameMatches && mimeMatches -> S01MetadataClass.EXACT
            nameMatches -> S01MetadataClass.MIME_MISMATCH
            !mimeMatches -> S01MetadataClass.NAME_AND_MIME_MISMATCH
            actualName == "$expectedName.${expectedMime.substringAfter('/')}" ->
                S01MetadataClass.NAME_APPENDED_EXTENSION
            else -> S01MetadataClass.OTHER_NAME
        }
    }
}
