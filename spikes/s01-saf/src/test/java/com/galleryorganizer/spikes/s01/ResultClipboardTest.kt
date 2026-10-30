package com.galleryorganizer.spikes.s01

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultClipboardTest {
    @Test
    fun `clipboard carries the complete S01 result json`() {
        val json = """{"decisionSingleDevice":"PASS"}"""
        var copied: Pair<String, String>? = null

        assertTrue(ResultClipboard { label, text -> copied = label to text }.copy(json))

        assertEquals("s01-result" to json, copied)
    }

    @Test
    fun `clipboard failure is reported without escaping`() {
        assertFalse(ResultClipboard { _, _ -> error("unavailable") }.copy("{}"))
    }
}
