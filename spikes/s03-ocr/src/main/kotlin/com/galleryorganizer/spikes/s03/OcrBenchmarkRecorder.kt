package com.galleryorganizer.spikes.s03

import java.text.Normalizer
import java.util.Locale

data class OcrTokenMatch(
    val expectedTokenCount: Int,
    val recognizedTokenCount: Int,
    val matchedTokenCount: Int,
)

data class OcrCategoryMatch(
    val labelMatched: Boolean,
    val numericMatched: Boolean,
)

object OcrTokenMatcher {
    private const val MAX_TOKENS_PER_SAMPLE = 4_096
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")

    fun match(expectedText: String, recognizedText: String): OcrTokenMatch {
        val expected = tokenize(expectedText)
        val recognized = tokenize(recognizedText)
        require(expected.isNotEmpty()) { "Expected OCR text must contain at least one token" }
        require(expected.size <= MAX_TOKENS_PER_SAMPLE)
        require(recognized.size <= MAX_TOKENS_PER_SAMPLE)

        val remaining = expected.groupingBy { it }.eachCount().toMutableMap()
        var matched = 0
        recognized.forEach { token ->
            val count = remaining[token] ?: 0
            if (count > 0) {
                matched += 1
                if (count == 1) remaining.remove(token) else remaining[token] = count - 1
            }
        }
        return OcrTokenMatch(expected.size, recognized.size, matched)
    }

    fun matchCategories(
        expectedLabel: String,
        expectedNumeric: String,
        recognizedText: String,
    ): OcrCategoryMatch {
        val label = tokenize(expectedLabel).single()
        val numeric = tokenize(expectedNumeric).single()
        val recognized = tokenize(recognizedText)
        require(recognized.size <= MAX_TOKENS_PER_SAMPLE)
        return OcrCategoryMatch(
            labelMatched = label in recognized,
            numericMatched = numeric in recognized,
        )
    }

    private fun tokenize(text: String): List<String> =
        tokenPattern.findAll(
            Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT),
        ).map { it.value }.toList()
}

sealed interface OcrRecognition {
    data class Completed(val text: String) : OcrRecognition
    data object Failed : OcrRecognition
}

object OcrNearestRankPercentile {
    fun p95(samples: List<Long>): Long {
        if (samples.isEmpty()) return 0
        require(samples.all { it >= 0 })
        val rank = ((samples.size.toLong() * 95 + 99) / 100).toInt()
        return samples.sorted()[rank - 1]
    }
}

class OcrBenchmarkRecorder(
    private val modelDelivery: OcrModelDelivery,
    private val internetPermissionDeclared: Boolean,
) {
    private val durationsNanos = mutableListOf<Long>()
    private var expectedTokenCount = 0
    private var recognizedTokenCount = 0
    private var matchedTokenCount = 0
    private var failedSampleCount = 0
    private var runCompleted = false

    @Synchronized
    fun recordSample(
        expectedText: String,
        recognition: OcrRecognition,
        durationNanos: Long,
    ) {
        require(durationNanos >= 0)
        ensureActive()
        val match = OcrTokenMatcher.match(
            expectedText,
            (recognition as? OcrRecognition.Completed)?.text.orEmpty(),
        )
        val nextExpectedCount = Math.addExact(expectedTokenCount, match.expectedTokenCount)
        val nextRecognizedCount = Math.addExact(recognizedTokenCount, match.recognizedTokenCount)
        val nextMatchedCount = Math.addExact(matchedTokenCount, match.matchedTokenCount)
        val nextFailedCount = if (recognition == OcrRecognition.Failed) {
            Math.addExact(failedSampleCount, 1)
        } else {
            failedSampleCount
        }
        durationsNanos += durationNanos
        expectedTokenCount = nextExpectedCount
        recognizedTokenCount = nextRecognizedCount
        matchedTokenCount = nextMatchedCount
        failedSampleCount = nextFailedCount
    }

    @Synchronized
    fun complete() {
        runCompleted = true
    }

    @Synchronized
    fun snapshot(): OcrBenchmarkMetrics = OcrBenchmarkMetrics(
        sampleCount = durationsNanos.size,
        expectedTokenCount = expectedTokenCount,
        recognizedTokenCount = recognizedTokenCount,
        matchedTokenCount = matchedTokenCount,
        failedSampleCount = failedSampleCount,
        p95Millis = nanosToRoundedUpMillis(OcrNearestRankPercentile.p95(durationsNanos)),
        runCompleted = runCompleted,
        modelDelivery = modelDelivery,
        internetPermissionDeclared = internetPermissionDeclared,
    )

    private fun ensureActive() {
        check(!runCompleted) { "Benchmark recording is already complete" }
    }

    private fun nanosToRoundedUpMillis(value: Long): Long =
        value / NANOS_PER_MILLI + if (value % NANOS_PER_MILLI == 0L) 0 else 1

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
