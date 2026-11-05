package com.galleryorganizer.spikes.s03.harness

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.galleryorganizer.spikes.s03.OcrBenchmarkMetrics
import com.galleryorganizer.spikes.s03.OcrBenchmarkResult
import com.galleryorganizer.spikes.s03.OcrRecognition
import com.galleryorganizer.spikes.s03.OcrTokenMatcher
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

const val S03_HARNESS_SCHEMA = 4
const val S03_CORPUS_VERSION = "real-font-paired-v2"
private const val S03_CORPUS_PAIR_COUNT = 50
private const val S03_CORPUS_FONT_NAME = "Noto Sans Regular"
private const val S03_CORPUS_FONT_LICENSE = "SIL Open Font License 1.1"
private const val S03_CORPUS_FONT_SHA256 =
    "bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d"
private const val S03_CORPUS_SHA256 =
    "80243d0a8efbe735be2ac244d2f55b25a9f3046a0a3443c8396c734ab78f8a16"
private const val S03_CORPUS_BYTES = 420_093L

enum class OcrNumericStyle { PLAIN, PADDED }

data class OcrFixture(
    val file: File,
    val expectedLabel: String,
    val expectedNumeric: String,
    val pairIndex: Int,
    val numericStyle: OcrNumericStyle,
) {
    val expectedText: String
        get() = "$expectedLabel $expectedNumeric"
}

data class OcrDiagnosticMetrics(
    val labelMatchedCount: Int,
    val numericMatchedCount: Int,
    val bothMatchedSampleCount: Int,
    val labelOnlySampleCount: Int,
    val numericOnlySampleCount: Int,
    val neitherMatchedSampleCount: Int,
    val plainSampleCount: Int,
    val paddedSampleCount: Int,
    val plainNumericMatchedCount: Int,
    val paddedNumericMatchedCount: Int,
    val completedPairCount: Int,
    val bothNumericMatchedPairCount: Int,
    val plainOnlyNumericMatchedPairCount: Int,
    val paddedOnlyNumericMatchedPairCount: Int,
    val neitherNumericMatchedPairCount: Int,
)

class OcrDiagnosticRecorder {
    private var labelMatched = 0
    private var numericMatched = 0
    private var both = 0
    private var labelOnly = 0
    private var numericOnly = 0
    private var neither = 0
    private var plainSamples = 0
    private var paddedSamples = 0
    private var plainNumericMatched = 0
    private var paddedNumericMatched = 0
    private val pairMatches = mutableMapOf<Int, PairMatch>()

    @Synchronized
    fun record(fixture: OcrFixture, recognition: OcrRecognition) {
        val match = OcrTokenMatcher.matchCategories(
            fixture.expectedLabel,
            fixture.expectedNumeric,
            (recognition as? OcrRecognition.Completed)?.text.orEmpty(),
        )
        if (match.labelMatched) labelMatched = Math.addExact(labelMatched, 1)
        if (match.numericMatched) numericMatched = Math.addExact(numericMatched, 1)
        when {
            match.labelMatched && match.numericMatched -> both = Math.addExact(both, 1)
            match.labelMatched -> labelOnly = Math.addExact(labelOnly, 1)
            match.numericMatched -> numericOnly = Math.addExact(numericOnly, 1)
            else -> neither = Math.addExact(neither, 1)
        }
        val pair = pairMatches.getOrPut(fixture.pairIndex) { PairMatch() }
        when (fixture.numericStyle) {
            OcrNumericStyle.PLAIN -> {
                check(pair.plain == null) { "Plain fixture already recorded" }
                pair.plain = match.numericMatched
                plainSamples = Math.addExact(plainSamples, 1)
                if (match.numericMatched) {
                    plainNumericMatched = Math.addExact(plainNumericMatched, 1)
                }
            }
            OcrNumericStyle.PADDED -> {
                check(pair.padded == null) { "Padded fixture already recorded" }
                pair.padded = match.numericMatched
                paddedSamples = Math.addExact(paddedSamples, 1)
                if (match.numericMatched) {
                    paddedNumericMatched = Math.addExact(paddedNumericMatched, 1)
                }
            }
        }
    }

    @Synchronized
    fun snapshot(): OcrDiagnosticMetrics {
        val completedPairs = pairMatches.values.filter { it.plain != null && it.padded != null }
        return OcrDiagnosticMetrics(
            labelMatched,
            numericMatched,
            both,
            labelOnly,
            numericOnly,
            neither,
            plainSamples,
            paddedSamples,
            plainNumericMatched,
            paddedNumericMatched,
            completedPairs.size,
            completedPairs.count { it.plain == true && it.padded == true },
            completedPairs.count { it.plain == true && it.padded == false },
            completedPairs.count { it.plain == false && it.padded == true },
            completedPairs.count { it.plain == false && it.padded == false },
        )
    }

    private data class PairMatch(
        var plain: Boolean? = null,
        var padded: Boolean? = null,
    )
}

data class OcrCorpusProvenance(
    val version: String,
    val storage: String,
    val sampleCount: Int,
    val totalBytes: Long,
    val sha256: String,
    val pairCount: Int,
    val fontName: String,
    val fontLicense: String,
    val fontSha256: String,
)

class AppPrivateOcrCorpus(context: Context) {
    private val root = File(context.filesDir, "s03-ocr-fixtures")
    private val assets = context.assets

    fun prepare(): Pair<List<OcrFixture>, OcrCorpusProvenance> {
        validateManifest()
        check(root.deleteRecursively() || !root.exists())
        check(root.mkdirs())
        val fixtures = buildList(S03_CORPUS_PAIR_COUNT * 2) {
            repeat(S03_CORPUS_PAIR_COUNT) { pairIndex ->
                add(copyFixture(pairIndex, OcrNumericStyle.PLAIN))
                add(copyFixture(pairIndex, OcrNumericStyle.PADDED))
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        fixtures.forEach { fixture ->
            fixture.file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
        }
        val totalBytes = fixtures.sumOf { it.file.length() }
        val corpusSha256 = digest.digest().toHex()
        check(totalBytes == S03_CORPUS_BYTES) { "Corpus byte count mismatch" }
        check(corpusSha256 == S03_CORPUS_SHA256) { "Corpus digest mismatch" }
        return fixtures to OcrCorpusProvenance(
            version = S03_CORPUS_VERSION,
            storage = "filesDir/s03-ocr-fixtures",
            sampleCount = fixtures.size,
            totalBytes = totalBytes,
            sha256 = corpusSha256,
            pairCount = S03_CORPUS_PAIR_COUNT,
            fontName = S03_CORPUS_FONT_NAME,
            fontLicense = S03_CORPUS_FONT_LICENSE,
            fontSha256 = S03_CORPUS_FONT_SHA256,
        )
    }

    private fun validateManifest() {
        val manifest = assets.open("$ASSET_ROOT/corpus.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        check(manifest.getString("version") == S03_CORPUS_VERSION)
        check(manifest.getInt("pairCount") == S03_CORPUS_PAIR_COUNT)
        check(manifest.getInt("sampleCount") == S03_CORPUS_PAIR_COUNT * 2)
        check(manifest.getString("fontName") == S03_CORPUS_FONT_NAME)
        check(manifest.getString("fontLicense") == S03_CORPUS_FONT_LICENSE)
        check(manifest.getString("fontSha256") == S03_CORPUS_FONT_SHA256)
        check(manifest.getLong("corpusBytes") == S03_CORPUS_BYTES)
        check(manifest.getString("corpusSha256") == S03_CORPUS_SHA256)
    }

    private fun copyFixture(pairIndex: Int, style: OcrNumericStyle): OcrFixture {
        val styleName = style.name.lowercase()
        val filename = "pair-${pairIndex.toString().padStart(3, '0')}-$styleName.png"
        val file = File(root, filename)
        assets.open("$ASSET_ROOT/images/$filename").use { input ->
            file.outputStream().buffered().use(input::copyTo)
        }
        return OcrFixture(
            file = file,
            expectedLabel = "ITEM",
            expectedNumeric = when (style) {
                OcrNumericStyle.PLAIN -> pairIndex.toString()
                OcrNumericStyle.PADDED -> pairIndex.toString().padStart(3, '0')
            },
            pairIndex = pairIndex,
            numericStyle = style,
        )
    }

    private companion object {
        const val ASSET_ROOT = "s03-real-font-paired-v2"
    }
}

data class OcrEvidence(
    val corpus: OcrCorpusProvenance,
    val metrics: OcrBenchmarkMetrics,
    val result: OcrBenchmarkResult,
    val diagnostics: OcrDiagnosticMetrics,
    val gmsCoreInstalled: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", S03_HARNESS_SCHEMA)
        .put("modelDelivery", metrics.modelDelivery.name)
        .put("internetPermissionDeclared", metrics.internetPermissionDeclared)
        .put("corpusVersion", corpus.version)
        .put("corpusStorage", corpus.storage)
        .put("corpusSampleCount", corpus.sampleCount)
        .put("corpusBytes", corpus.totalBytes)
        .put("corpusSha256", corpus.sha256)
        .put("corpusPairCount", corpus.pairCount)
        .put("corpusFontName", corpus.fontName)
        .put("corpusFontLicense", corpus.fontLicense)
        .put("corpusFontSha256", corpus.fontSha256)
        .put("deviceManufacturer", Build.MANUFACTURER.orEmpty().ifBlank { "unknown" })
        .put("deviceModel", Build.MODEL.orEmpty().ifBlank { "unknown" })
        .put("deviceApiLevel", Build.VERSION.SDK_INT)
        .put("deviceRelease", Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" })
        .put("deviceSecurityPatch", Build.VERSION.SECURITY_PATCH.orEmpty().ifBlank { "unknown" })
        .put("gmsCoreInstalled", gmsCoreInstalled)
        .put("deviceFingerprintSha256", sha256(Build.FINGERPRINT.orEmpty()))
        .put("sampleCount", metrics.sampleCount)
        .put("expectedTokenCount", metrics.expectedTokenCount)
        .put("recognizedTokenCount", metrics.recognizedTokenCount)
        .put("matchedTokenCount", metrics.matchedTokenCount)
        .put("failedSampleCount", metrics.failedSampleCount)
        .put("labelMatchedCount", diagnostics.labelMatchedCount)
        .put("numericMatchedCount", diagnostics.numericMatchedCount)
        .put("bothMatchedSampleCount", diagnostics.bothMatchedSampleCount)
        .put("labelOnlySampleCount", diagnostics.labelOnlySampleCount)
        .put("numericOnlySampleCount", diagnostics.numericOnlySampleCount)
        .put("neitherMatchedSampleCount", diagnostics.neitherMatchedSampleCount)
        .put("plainSampleCount", diagnostics.plainSampleCount)
        .put("paddedSampleCount", diagnostics.paddedSampleCount)
        .put("plainNumericMatchedCount", diagnostics.plainNumericMatchedCount)
        .put("paddedNumericMatchedCount", diagnostics.paddedNumericMatchedCount)
        .put("completedPairCount", diagnostics.completedPairCount)
        .put("bothNumericMatchedPairCount", diagnostics.bothNumericMatchedPairCount)
        .put("plainOnlyNumericMatchedPairCount", diagnostics.plainOnlyNumericMatchedPairCount)
        .put("paddedOnlyNumericMatchedPairCount", diagnostics.paddedOnlyNumericMatchedPairCount)
        .put("neitherNumericMatchedPairCount", diagnostics.neitherNumericMatchedPairCount)
        .put("precisionPermille", result.precisionPermille)
        .put("recallPermille", result.recallPermille)
        .put("p95Millis", metrics.p95Millis)
        .put("runCompleted", metrics.runCompleted)
        .put("decision", result.decision.name)
        .put("violations", result.violations.joinToString(",") { it.name })
}

@Suppress("DEPRECATION")
fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess

class ResultClipboard(private val context: Context) {
    fun copy(json: String): Boolean {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
        return runCatching {
            manager.setPrimaryClip(ClipData.newPlainText("s03-ocr-result", json))
        }.isSuccess
    }
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
