package com.galleryorganizer.data.ocr

import android.graphics.Bitmap
import com.galleryorganizer.domain.ManualOcrRecognition
import com.galleryorganizer.domain.ManualOcrRuleHit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidManualOcrRecognizerTest {
    @Test
    fun `decode sampling enforces source and output pixel bounds`() {
        assertEquals(2, sampleSize(4_000, 3_000))
        assertNull(sampleSize(8_000, 6_000))
        assertNull(sampleSize(0, 100))
    }

    @Test
    fun `default policy fails before source or model access`() = runTest {
        var sourceCalls = 0
        var engineCalls = 0
        val recognizer = AndroidManualOcrRecognizer(
            source = ManualOcrBitmapSource {
                sourceCalls += 1
                error("source must stay closed")
            },
            engineFactory = ManualOcrTextEngineFactory {
                engineCalls += 1
                error("model must stay closed")
            },
            rules = ManualOcrRuleSet { _, _ -> error("rules must stay closed") },
        )

        assertEquals(ManualOcrRecognition.Failed, recognizer.recognize(CONTENT_KEY))
        assertEquals(0, sourceCalls)
        assertEquals(0, engineCalls)
    }

    @Test
    fun `success emits bounded hits and releases private resources`() = runTest {
        val bitmap = bitmap()
        val engine = RecordingEngine { PRIVATE_TEXT }
        val recognizer = recognizer(bitmap, engine) { contentKey, text ->
            assertEquals(CONTENT_KEY, contentKey)
            assertEquals(PRIVATE_TEXT, text)
            listOf(ManualOcrRuleHit(contentKey, "ad.phone", 900, 1))
        }

        val result = assertIs<ManualOcrRecognition.Hits>(recognizer.recognize(CONTENT_KEY))

        assertEquals(listOf("ad.phone"), result.values.map { it.ruleId })
        assertTrue(engine.closed)
        assertTrue(bitmap.isRecycled)
        assertTrue(PRIVATE_TEXT !in result.toString())
    }

    @Test
    fun `timeout fails closed and releases resources`() = runTest {
        val bitmap = bitmap()
        val engine = RecordingEngine {
            delay(Long.MAX_VALUE)
            PRIVATE_TEXT
        }
        val recognizer = recognizer(
            bitmap,
            engine,
            policy = ManualOcrAdapterPolicy(enabled = true, timeoutMillis = 100),
        ) { _, _ -> emptyList() }

        assertEquals(ManualOcrRecognition.Failed, recognizer.recognize(CONTENT_KEY))
        assertTrue(engine.closed)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun `caller cancellation propagates and releases resources`() = runTest {
        val bitmap = bitmap()
        val started = CompletableDeferred<Unit>()
        val engine = RecordingEngine {
            started.complete(Unit)
            awaitCancellation()
        }
        val recognizer = recognizer(bitmap, engine) { _, _ -> emptyList() }
        val run = async { recognizer.recognize(CONTENT_KEY) }
        started.await()

        run.cancel()

        assertFailsWith<CancellationException> { run.await() }
        assertTrue(engine.closed)
        assertTrue(bitmap.isRecycled)
    }

    private fun recognizer(
        bitmap: Bitmap,
        engine: RecordingEngine,
        policy: ManualOcrAdapterPolicy = ManualOcrAdapterPolicy(enabled = true),
        rules: ManualOcrRuleSet,
    ) = AndroidManualOcrRecognizer(
        source = ManualOcrBitmapSource { bitmap },
        engineFactory = ManualOcrTextEngineFactory { engine },
        rules = rules,
        policy = policy,
    )

    private fun bitmap() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    private class RecordingEngine(
        private val result: suspend () -> String,
    ) : ManualOcrTextEngine {
        var closed = false

        override suspend fun recognize(bitmap: Bitmap): String = result()

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val CONTENT_KEY = "mediastore:image:42"
        const val PRIVATE_TEXT = "private phone 123456"
    }
}
