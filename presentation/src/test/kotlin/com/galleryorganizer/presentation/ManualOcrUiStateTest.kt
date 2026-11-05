package com.galleryorganizer.presentation

import com.galleryorganizer.domain.ManualOcrEvidenceDraft
import com.galleryorganizer.domain.ManualOcrRecognition
import com.galleryorganizer.domain.ManualOcrRecognizer
import com.galleryorganizer.domain.ManualOcrRunResult
import com.galleryorganizer.domain.ManualOcrRunner
import com.galleryorganizer.domain.ManualOcrScanRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualOcrUiStateTest {
    @Test
    fun `production state keeps manual OCR unavailable pending device evidence`() {
        val initial = ManualOcrUiState.production()
        val requested = ManualOcrReducer.reduce(
            initial,
            ManualOcrEvent.Request(request("session-1", "a")),
        )

        assertEquals(ManualOcrAvailability.DISABLED_PENDING_NO_GMS_EVIDENCE, initial.availability)
        assertFalse(initial.canRequest)
        assertEquals(initial, requested)
        assertNull(requested.request)
    }

    @Test
    fun `review-only state requires confirmation and accepts matching masked result`() = runBlocking {
        val request = request("session-1", "a", "b")
        val result = ManualOcrRunner(
            object : ManualOcrRecognizer {
                override suspend fun recognize(contentKey: String) =
                    ManualOcrRecognition.Hits(emptyList())
            },
        ).run(request)
        val review = ManualOcrUiState.reviewOnly()
        val requested = ManualOcrReducer.reduce(review, ManualOcrEvent.Request(request))
        val running = ManualOcrReducer.reduce(
            requested,
            ManualOcrEvent.Confirm(request.sessionId),
        )
        val completed = ManualOcrReducer.reduce(
            running,
            ManualOcrEvent.RunFinished(request.sessionId, result),
        )

        assertEquals(ManualOcrPhase.AWAITING_CONFIRMATION, requested.phase)
        assertEquals(ManualOcrPhase.RUNNING, running.phase)
        assertEquals(ManualOcrPhase.COMPLETED, completed.phase)
        assertEquals(2, completed.processedSubjects)
        assertEquals(0, completed.evidenceCount)
        assertNull(completed.failure)
    }

    @Test
    fun `stale and foreign terminal results fail closed without exposing evidence detail`() {
        val request = request("session-1", "a")
        val running = ManualOcrReducer.reduce(
            ManualOcrReducer.reduce(
                ManualOcrUiState.reviewOnly(),
                ManualOcrEvent.Request(request),
            ),
            ManualOcrEvent.Confirm(request.sessionId),
        )
        val stale = ManualOcrReducer.reduce(
            running,
            ManualOcrEvent.RunFinished(
                "older-session",
                completedResult(request("older-session", "a")),
            ),
        )
        val invalid = ManualOcrReducer.reduce(
            stale,
            ManualOcrEvent.RunFinished(
                request.sessionId,
                completedResult(request, evidenceContentKey = "foreign"),
            ),
        )

        assertEquals(running, stale)
        assertEquals(ManualOcrPhase.FAILED, invalid.phase)
        assertEquals(ManualOcrUiFailure.INVALID_RESULT, invalid.failure)
        assertEquals(0, invalid.evidenceCount)
        assertTrue(invalid.request?.contentKeys == listOf("a"))
    }

    private fun request(sessionId: String, vararg keys: String) =
        ManualOcrScanRequest(sessionId, keys.toList(), userInitiated = true)

    private fun completedResult(
        request: ManualOcrScanRequest,
        evidenceContentKey: String? = null,
    ): ManualOcrRunResult.Completed {
        var session = com.galleryorganizer.domain.ManualOcrScanSession.create(request).start()
        request.contentKeys.forEach { session = session.record(0) }
        val evidence = evidenceContentKey?.let {
            listOf(
                ManualOcrEvidenceDraft(
                    evidenceId = "ocr:test",
                    contentKey = it,
                    type = "OCR_ADVERTISEMENT",
                    scorePermille = 900,
                    maskedDetail = "Potential advertisement text (masked)",
                    analysisVersion = 1,
                    evidenceKey = "test",
                ),
            )
        }.orEmpty()
        return ManualOcrRunResult.Completed(session.complete(), evidence)
    }
}
