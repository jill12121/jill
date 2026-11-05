package com.galleryorganizer.spikes.s03

import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OcrBenchmarkRecorderTest {
    @Test
    fun `token matcher normalizes unicode case punctuation and preserves multiplicity`() {
        assertEquals(
            OcrTokenMatch(
                expectedTokenCount = 3,
                recognizedTokenCount = 4,
                matchedTokenCount = 3,
            ),
            OcrTokenMatcher.match(
                expectedText = "SALE, sale; Ａ1",
                recognizedText = "sale A1 SALE extra",
            ),
        )
        assertEquals(
            1,
            OcrTokenMatcher.match("offer offer", "offer").matchedTokenCount,
        )
    }

    @Test
    fun `token matcher rejects an empty expected fixture`() {
        assertFailsWith<IllegalArgumentException> {
            OcrTokenMatcher.match(" -- ", "anything")
        }
    }

    @Test
    fun `category matcher reports label and numeric outcomes without raw output`() {
        assertEquals(
            OcrCategoryMatch(labelMatched = true, numericMatched = false),
            OcrTokenMatcher.matchCategories("ITEM", "007", "item O07"),
        )
        assertEquals(
            OcrCategoryMatch(labelMatched = false, numericMatched = true),
            OcrTokenMatcher.matchCategories("ITEM", "007", "offer 007"),
        )
    }

    @Test
    fun `nearest-rank p95 is stable for unsorted samples`() {
        assertEquals(19L, OcrNearestRankPercentile.p95((1L..20L).toList().reversed()))
        assertEquals(7L, OcrNearestRankPercentile.p95(listOf(7L)))
        assertEquals(0L, OcrNearestRankPercentile.p95(emptyList()))
    }

    @Test
    fun `snapshot aggregates token matches failures and rounded p95`() {
        val recorder = recorder()
        recorder.recordSample(
            "sale now",
            OcrRecognition.Completed("sale extra"),
            durationNanos = 10_000_001,
        )
        recorder.recordSample(
            "call today",
            OcrRecognition.Failed,
            durationNanos = 20_000_000,
        )
        recorder.complete()

        assertEquals(
            OcrBenchmarkMetrics(
                sampleCount = 2,
                expectedTokenCount = 4,
                recognizedTokenCount = 2,
                matchedTokenCount = 1,
                failedSampleCount = 1,
                p95Millis = 20,
                runCompleted = true,
                modelDelivery = OcrModelDelivery.BUNDLED,
                internetPermissionDeclared = false,
            ),
            recorder.snapshot(),
        )
    }

    @Test
    fun `empty snapshot is pending and completion blocks late samples`() {
        val recorder = recorder()

        assertFalse(recorder.snapshot().runCompleted)
        recorder.complete()
        assertTrue(recorder.snapshot().runCompleted)
        assertFailsWith<IllegalStateException> {
            recorder.recordSample("sale", OcrRecognition.Completed("sale"), 1)
        }
    }

    @Test
    fun `concurrent samples are counted without loss`() {
        val recorder = recorder()
        val workers = List(8) {
            thread {
                repeat(100) {
                    recorder.recordSample(
                        "sale now",
                        OcrRecognition.Completed("sale now"),
                        durationNanos = 1_000_000,
                    )
                }
            }
        }
        workers.forEach(Thread::join)
        recorder.complete()

        val metrics = recorder.snapshot()
        assertEquals(800, metrics.sampleCount)
        assertEquals(1_600, metrics.expectedTokenCount)
        assertEquals(1_600, metrics.recognizedTokenCount)
        assertEquals(1_600, metrics.matchedTokenCount)
        assertEquals(1, metrics.p95Millis)
    }

    @Test
    fun `negative duration is rejected without mutating the recorder`() {
        val recorder = recorder()

        assertFailsWith<IllegalArgumentException> {
            recorder.recordSample("sale", OcrRecognition.Completed("sale"), -1)
        }
        assertEquals(0, recorder.snapshot().sampleCount)
    }

    private fun recorder() = OcrBenchmarkRecorder(
        modelDelivery = OcrModelDelivery.BUNDLED,
        internetPermissionDeclared = false,
    )
}
