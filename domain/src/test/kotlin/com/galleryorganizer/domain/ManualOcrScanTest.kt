package com.galleryorganizer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualOcrScanTest {
    @Test
    fun `request requires explicit user initiation and bounded unique subjects`() {
        assertFailsWith<IllegalArgumentException> {
            ManualOcrScanRequest(
                sessionId = "ocr-1",
                contentKeys = listOf("content-1"),
                userInitiated = false,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            request(List(101) { "content-$it" })
        }
        assertFailsWith<IllegalArgumentException> {
            request(listOf("content-1", "content-1"))
        }

        assertEquals(100, request(List(100) { "content-$it" }).contentKeys.size)
    }

    @Test
    fun `session permits only explicit manual scan transitions`() {
        val created = ManualOcrScanSession.create(request(listOf("a", "b")))

        assertEquals(ManualOcrScanState.AWAITING_CONFIRMATION, created.state)
        assertFailsWith<IllegalStateException> { created.record(evidenceCount = 0) }
        val running = created.start()
        val paused = running.pause()
        val resumed = paused.resume()
        val cancelled = resumed.cancel()

        assertEquals(ManualOcrScanState.CANCELLED, cancelled.state)
        assertFailsWith<IllegalStateException> { cancelled.resume() }
        assertFailsWith<IllegalStateException> { created.complete() }
    }

    @Test
    fun `progress is bounded and completion requires every subject`() {
        val running = ManualOcrScanSession.create(request(listOf("a", "b"))).start()
        val first = running.record(evidenceCount = 2)

        assertEquals(1, first.processedSubjects)
        assertEquals(2, first.evidenceCount)
        assertFailsWith<IllegalStateException> { first.complete() }
        assertFailsWith<IllegalArgumentException> { first.record(evidenceCount = -1) }

        val completed = first.record(evidenceCount = 0).complete()
        assertEquals(ManualOcrScanState.COMPLETED, completed.state)
        assertEquals(2, completed.processedSubjects)
        assertEquals(2, completed.evidenceCount)
    }

    @Test
    fun `evidence identity is stable and exposes no recognized text field`() {
        val hit = ManualOcrRuleHit(
            contentKey = "sha256:abc",
            ruleId = "ad-contact-density",
            scorePermille = 875,
            analysisVersion = 1,
        )

        val first = ManualOcrEvidenceFactory.create(hit)
        val second = ManualOcrEvidenceFactory.create(hit)

        assertEquals(first, second)
        assertEquals("OCR_ADVERTISEMENT", first.type)
        assertEquals("Potential advertisement text (masked)", first.maskedDetail)
        assertEquals(875, first.scorePermille)
        assertTrue(first.evidenceId.startsWith("ocr:"))
        assertEquals(64, first.evidenceKey.length)
        assertFalse(first.toString().contains("recognizedText"))
    }

    private fun request(contentKeys: List<String>) = ManualOcrScanRequest(
        sessionId = "ocr-1",
        contentKeys = contentKeys,
        userInitiated = true,
    )
}
