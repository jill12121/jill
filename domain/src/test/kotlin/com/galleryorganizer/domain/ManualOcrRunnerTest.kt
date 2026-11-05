package com.galleryorganizer.domain

import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ManualOcrRunnerTest {
    @Test
    fun `runner processes bounded subjects and returns masked evidence`() = runBlocking {
        val recognizer = FakeManualOcrRecognizer(
            mapOf(
                "a" to hits(hit("a", "ad-contact")),
                "b" to hits(),
            ),
        )

        val result = assertIs<ManualOcrRunResult.Completed>(
            ManualOcrRunner(recognizer).run(request("a", "b")),
        )

        assertEquals(listOf("a", "b"), recognizer.calls)
        assertEquals(ManualOcrScanState.COMPLETED, result.session.state)
        assertEquals(2, result.session.processedSubjects)
        assertEquals(listOf("Potential advertisement text (masked)"), result.evidence.map { it.maskedDetail })
    }

    @Test
    fun `recognizer failure stops before later subjects and keeps prior evidence`() = runBlocking {
        val recognizer = FakeManualOcrRecognizer(
            mapOf(
                "a" to hits(hit("a", "ad-contact")),
                "b" to ManualOcrRecognition.Failed,
                "c" to hits(hit("c", "ad-promo")),
            ),
        )

        val result = assertIs<ManualOcrRunResult.Failed>(
            ManualOcrRunner(recognizer).run(request("a", "b", "c")),
        )

        assertEquals(ManualOcrRunFailure.RECOGNIZER_FAILURE, result.failure)
        assertEquals(listOf("a", "b"), recognizer.calls)
        assertEquals(1, result.session.processedSubjects)
        assertEquals(1, result.evidence.size)
    }

    @Test
    fun `foreign content result fails closed`() = runBlocking {
        val result = assertIs<ManualOcrRunResult.Failed>(
            ManualOcrRunner(
                FakeManualOcrRecognizer(mapOf("a" to hits(hit("other", "ad-contact")))),
            ).run(request("a")),
        )

        assertEquals(ManualOcrRunFailure.INVALID_RESULT, result.failure)
        assertEquals(0, result.session.processedSubjects)
        assertEquals(emptyList(), result.evidence)
    }

    @Test
    fun `recognizer exception fails closed without processing later subjects`() = runBlocking {
        val recognizer = ThrowingManualOcrRecognizer(
            failureKey = "b",
            failure = IllegalStateException("raw adapter detail"),
        )

        val result = assertIs<ManualOcrRunResult.Failed>(
            ManualOcrRunner(recognizer).run(request("a", "b", "c")),
        )

        assertEquals(ManualOcrRunFailure.RECOGNIZER_FAILURE, result.failure)
        assertEquals("b", result.contentKey)
        assertEquals(listOf("a", "b"), recognizer.calls)
        assertEquals(1, result.session.processedSubjects)
        assertEquals(emptyList(), result.evidence)
    }

    @Test
    fun `recognizer cancellation remains cooperative`() {
        assertFailsWith<CancellationException> {
            runBlocking {
                ManualOcrRunner(
                    ThrowingManualOcrRecognizer(
                        failureKey = "a",
                        failure = CancellationException("cancelled"),
                    ),
                ).run(request("a"))
            }
        }
    }

    private fun request(vararg keys: String) =
        ManualOcrScanRequest("session-1", keys.toList(), userInitiated = true)

    private fun hit(contentKey: String, ruleId: String) =
        ManualOcrRuleHit(contentKey, ruleId, 900, 1)

    private fun hits(vararg values: ManualOcrRuleHit) =
        ManualOcrRecognition.Hits(values.toList())
}

private class FakeManualOcrRecognizer(
    private val results: Map<String, ManualOcrRecognition>,
) : ManualOcrRecognizer {
    val calls = mutableListOf<String>()

    override suspend fun recognize(contentKey: String): ManualOcrRecognition {
        calls += contentKey
        return results.getValue(contentKey)
    }
}

private class ThrowingManualOcrRecognizer(
    private val failureKey: String,
    private val failure: RuntimeException,
) : ManualOcrRecognizer {
    val calls = mutableListOf<String>()

    override suspend fun recognize(contentKey: String): ManualOcrRecognition {
        calls += contentKey
        if (contentKey == failureKey) {
            throw failure
        }
        return ManualOcrRecognition.Hits(emptyList())
    }
}
