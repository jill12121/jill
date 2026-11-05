package com.galleryorganizer.spikes.s03.harness

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.galleryorganizer.spikes.s03.OcrBenchmarkClassifier
import com.galleryorganizer.spikes.s03.OcrBenchmarkBudget
import com.galleryorganizer.spikes.s03.OcrBenchmarkMetrics
import com.galleryorganizer.spikes.s03.OcrModelDelivery
import com.galleryorganizer.spikes.s03.OcrRecognition
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrHarnessCoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `merged harness manifest requests no permissions`() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )

        assertTrue(info.requestedPermissions.orEmpty().isEmpty())
    }

    @Test
    fun `corpus is deterministic bounded and app private`() {
        val store = AppPrivateOcrCorpus(context)
        val (firstFixtures, first) = store.prepare()
        val (_, second) = store.prepare()

        assertEquals(S03_CORPUS_VERSION, second.version)
        assertEquals(100, second.sampleCount)
        assertEquals(50, second.pairCount)
        assertEquals("Noto Sans Regular", second.fontName)
        assertEquals("SIL Open Font License 1.1", second.fontLicense)
        assertEquals(
            "bfb7bb691513f12e734dc346c03a03f784912432d7e3fa8e56efcf906fe86b3d",
            second.fontSha256,
        )
        assertEquals(420_093, second.totalBytes)
        assertEquals(
            "80243d0a8efbe735be2ac244d2f55b25a9f3046a0a3443c8396c734ab78f8a16",
            second.sha256,
        )
        assertEquals(first.sha256, second.sha256)
        assertTrue(firstFixtures.all {
            it.file.canonicalPath.startsWith(context.filesDir.canonicalPath) &&
                it.file.isFile
        })
        assertEquals(
            listOf("0", "000", "1", "001"),
            firstFixtures.take(4).map(OcrFixture::expectedNumeric),
        )
        val dimensions = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(firstFixtures.first().file.absolutePath, dimensions)
        assertEquals(480, dimensions.outWidth)
        assertEquals(128, dimensions.outHeight)
    }

    @Test
    fun `diagnostics separate plain and padded outcomes by pair`() {
        val recorder = OcrDiagnosticRecorder()
        recorder.record(fixture(0, OcrNumericStyle.PLAIN), OcrRecognition.Completed("item 0"))
        recorder.record(fixture(0, OcrNumericStyle.PADDED), OcrRecognition.Completed("item 000"))
        recorder.record(fixture(1, OcrNumericStyle.PLAIN), OcrRecognition.Completed("item 1"))
        recorder.record(fixture(1, OcrNumericStyle.PADDED), OcrRecognition.Completed("item O01"))
        recorder.record(fixture(2, OcrNumericStyle.PLAIN), OcrRecognition.Completed("item O"))
        recorder.record(fixture(2, OcrNumericStyle.PADDED), OcrRecognition.Completed("offer 002"))
        recorder.record(fixture(3, OcrNumericStyle.PLAIN), OcrRecognition.Completed("item O"))
        recorder.record(fixture(3, OcrNumericStyle.PADDED), OcrRecognition.Failed)

        assertEquals(
            OcrDiagnosticMetrics(
                labelMatchedCount = 6,
                numericMatchedCount = 4,
                bothMatchedSampleCount = 3,
                labelOnlySampleCount = 3,
                numericOnlySampleCount = 1,
                neitherMatchedSampleCount = 1,
                plainSampleCount = 4,
                paddedSampleCount = 4,
                plainNumericMatchedCount = 2,
                paddedNumericMatchedCount = 2,
                completedPairCount = 4,
                bothNumericMatchedPairCount = 1,
                plainOnlyNumericMatchedPairCount = 1,
                paddedOnlyNumericMatchedPairCount = 1,
                neitherNumericMatchedPairCount = 1,
            ),
            recorder.snapshot(),
        )
    }

    @Test
    fun `evidence exports aggregates without fixture or recognized text`() {
        val secretExpected = "ITEM 007"
        val secretRecognized = "PRIVATE OCR PAYLOAD"
        val metrics = OcrBenchmarkMetrics(
            100, 200, 200, 200, 0, 40, true,
            OcrModelDelivery.BUNDLED, false,
        )
        val result = OcrBenchmarkClassifier.classify(
            metrics,
            OcrBenchmarkBudget(100, 950, 950, 750, 0),
        )
        val json = OcrEvidence(
            OcrCorpusProvenance(
                S03_CORPUS_VERSION,
                "filesDir/s03-ocr-fixtures",
                100,
                420_093,
                "a".repeat(64),
                50,
                "Noto Sans Regular",
                "SIL Open Font License 1.1",
                "b".repeat(64),
            ),
            metrics,
            result,
            OcrDiagnosticMetrics(
                100, 100, 100, 0, 0, 0,
                50, 50, 50, 50, 50, 50, 0, 0, 0,
            ),
            gmsCoreInstalled = false,
        ).toJson().toString()

        assertTrue(json.contains("\"schema\":4"))
        assertTrue(json.contains("\"corpusPairCount\":50"))
        assertTrue(json.contains("\"labelMatchedCount\":100"))
        assertTrue(json.contains("\"numericMatchedCount\":100"))
        assertTrue(json.contains("\"plainNumericMatchedCount\":50"))
        assertTrue(json.contains("\"paddedNumericMatchedCount\":50"))
        assertTrue(json.contains("\"bothNumericMatchedPairCount\":50"))
        assertTrue(json.contains("\"decision\":\"PASS\""))
        assertTrue(json.contains("\"modelDelivery\":\"BUNDLED\""))
        assertTrue(json.contains("\"gmsCoreInstalled\":false"))
        assertFalse(json.contains(secretExpected))
        assertFalse(json.contains(secretRecognized))
        assertFalse(json.contains("expectedText"))
        assertFalse(json.contains("recognizedText"))
    }

    private fun fixture(pairIndex: Int, style: OcrNumericStyle): OcrFixture = OcrFixture(
        file = File(context.filesDir, "unused-$pairIndex-${style.name}.png"),
        expectedLabel = "ITEM",
        expectedNumeric = when (style) {
            OcrNumericStyle.PLAIN -> pairIndex.toString()
            OcrNumericStyle.PADDED -> pairIndex.toString().padStart(3, '0')
        },
        pairIndex = pairIndex,
        numericStyle = style,
    )
}
