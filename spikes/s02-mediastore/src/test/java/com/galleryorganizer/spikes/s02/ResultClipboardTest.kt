package com.galleryorganizer.spikes.s02

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultClipboardTest {
    @Test
    fun `clipboard carries the complete S02 result json`() {
        val json = """{"decisionS02SingleDevice":"PASS"}"""
        var copied: Pair<String, String>? = null

        assertTrue(ResultClipboard { label, text -> copied = label to text }.copy(json))

        assertEquals("s02-result" to json, copied)
    }

    @Test
    fun `clipboard failure is reported without escaping`() {
        assertFalse(ResultClipboard { _, _ -> error("unavailable") }.copy("{}"))
    }

    @Test
    fun `clipboard reads and trims a fixture token`() {
        val clipboard = ResultClipboard(
            writeText = { _, _ -> },
            readText = { "  123e4567-e89b-12d3-a456-426614174000\n" },
        )

        assertEquals("123e4567-e89b-12d3-a456-426614174000", clipboard.read())
    }

    @Test
    fun `clipboard read failure is contained`() {
        val clipboard = ResultClipboard(
            writeText = { _, _ -> },
            readText = { error("unavailable") },
        )

        assertEquals(null, clipboard.read())
    }
}
