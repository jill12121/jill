package com.galleryorganizer.spikes.s01

import org.junit.Assert.assertEquals
import org.junit.Test

class S01MetadataRulesTest {
    @Test
    fun `classifies exact and provider-appended extension names`() {
        assertEquals(
            S01MetadataClass.EXACT,
            S01MetadataRules.classify(".s01.webp.part", "image/webp", ".s01.webp.part", "image/webp"),
        )
        assertEquals(
            S01MetadataClass.NAME_APPENDED_EXTENSION,
            S01MetadataRules.classify(
                ".s01.webp.part",
                "image/webp",
                ".s01.webp.part.webp",
                "image/webp",
            ),
        )
    }

    @Test
    fun `classifies mime and other name mismatches without exposing names`() {
        assertEquals(
            S01MetadataClass.MIME_MISMATCH,
            S01MetadataRules.classify("expected", "image/webp", "expected", "image/png"),
        )
        assertEquals(
            S01MetadataClass.NAME_AND_MIME_MISMATCH,
            S01MetadataRules.classify("expected", "image/webp", "other", "image/png"),
        )
        assertEquals(
            S01MetadataClass.OTHER_NAME,
            S01MetadataRules.classify("expected", "image/webp", "other", "image/webp"),
        )
    }
}
