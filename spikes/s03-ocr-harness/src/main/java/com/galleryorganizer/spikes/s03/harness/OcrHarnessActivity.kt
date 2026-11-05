package com.galleryorganizer.spikes.s03.harness

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.galleryorganizer.spikes.s03.OcrBenchmarkBudget
import com.galleryorganizer.spikes.s03.OcrBenchmarkClassifier
import com.galleryorganizer.spikes.s03.OcrBenchmarkRecorder
import com.galleryorganizer.spikes.s03.OcrModelDelivery
import com.galleryorganizer.spikes.s03.OcrRecognition
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.concurrent.thread

class OcrHarnessActivity : Activity() {
    private val recognizerDelegate = lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val recognizer by recognizerDelegate
    private lateinit var status: TextView
    private lateinit var metrics: TextView
    private lateinit var progress: ProgressBar
    private lateinit var start: Button
    private lateinit var copy: Button
    private var generation = 0
    private var lastResultJson: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "Ready - bundled OCR, app-private corpus" }
        metrics = TextView(this).apply { text = "No benchmark evidence yet" }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        start = Button(this).apply {
            text = "Run benchmark"
            setOnClickListener { beginRun() }
        }
        copy = Button(this).apply {
            text = "Copy result JSON"
            isEnabled = false
            setOnClickListener {
                val json = lastResultJson ?: return@setOnClickListener
                status.text = if (ResultClipboard(this@OcrHarnessActivity).copy(json)) {
                    "Aggregate result copied"
                } else {
                    "Clipboard unavailable"
                }
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                addView(status)
                addView(progress)
                addView(metrics)
                addView(start)
                addView(copy)
            },
        )
    }

    private fun beginRun() {
        val run = ++generation
        start.isEnabled = false
        copy.isEnabled = false
        progress.progress = 0
        status.text = "Preparing versioned fixtures..."
        thread(name = "s03-fixture-prepare") {
            val prepared = runCatching { AppPrivateOcrCorpus(this).prepare() }
            runOnUiThread {
                if (run != generation) return@runOnUiThread
                prepared.fold(
                    onSuccess = { (fixtures, provenance) ->
                        status.text = "Running bundled OCR..."
                        val diagnostics = OcrDiagnosticRecorder()
                        runNext(run, fixtures, provenance, OcrBenchmarkRecorder(
                            modelDelivery = OcrModelDelivery.BUNDLED,
                            internetPermissionDeclared = packageManager.getPackageInfo(
                                packageName,
                                PackageManager.GET_PERMISSIONS,
                            ).requestedPermissions.orEmpty().contains(
                                android.Manifest.permission.INTERNET,
                            ),
                        ), diagnostics, 0)
                    },
                    onFailure = { finishWithError(it) },
                )
            }
        }
    }

    private fun runNext(
        run: Int,
        fixtures: List<OcrFixture>,
        provenance: OcrCorpusProvenance,
        recorder: OcrBenchmarkRecorder,
        diagnostics: OcrDiagnosticRecorder,
        index: Int,
    ) {
        if (run != generation) return
        if (index == fixtures.size) {
            recorder.complete()
            publish(provenance, recorder, diagnostics)
            return
        }
        val fixture = fixtures[index]
        val bitmap = BitmapFactory.decodeFile(fixture.file.absolutePath)
        if (bitmap == null) {
            val recognition = OcrRecognition.Failed
            recorder.recordSample(fixture.expectedText, recognition, 0)
            diagnostics.record(fixture, recognition)
            runNext(run, fixtures, provenance, recorder, diagnostics, index + 1)
            return
        }
        val started = System.nanoTime()
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                bitmap.recycle()
                if (run != generation) return@addOnSuccessListener
                val recognition = OcrRecognition.Completed(result.text)
                recorder.recordSample(
                    fixture.expectedText,
                    recognition,
                    System.nanoTime() - started,
                )
                diagnostics.record(fixture, recognition)
                progress.progress = index + 1
                metrics.text = "${index + 1} / ${fixtures.size} samples"
                runNext(run, fixtures, provenance, recorder, diagnostics, index + 1)
            }
            .addOnFailureListener {
                bitmap.recycle()
                if (run != generation) return@addOnFailureListener
                val recognition = OcrRecognition.Failed
                recorder.recordSample(
                    fixture.expectedText,
                    recognition,
                    System.nanoTime() - started,
                )
                diagnostics.record(fixture, recognition)
                runNext(run, fixtures, provenance, recorder, diagnostics, index + 1)
            }
    }

    private fun publish(
        provenance: OcrCorpusProvenance,
        recorder: OcrBenchmarkRecorder,
        diagnostics: OcrDiagnosticRecorder,
    ) {
        val snapshot = recorder.snapshot()
        val result = OcrBenchmarkClassifier.classify(
            snapshot,
            OcrBenchmarkBudget(100, 950, 950, 750, 0),
        )
        val json = OcrEvidence(
            provenance,
            snapshot,
            result,
            diagnostics.snapshot(),
            gmsCoreInstalled = isPackageInstalled(this, "com.google.android.gms"),
        ).toJson()
        val output = File(filesDir, "s03-results/latest.json")
        val writeFailure = runCatching {
            check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
            output.writeText(json.toString(2))
        }.exceptionOrNull()
        lastResultJson = json.toString()
        copy.isEnabled = true
        start.isEnabled = true
        progress.progress = 100
        status.text = writeFailure?.let { "Result write failed: ${it.javaClass.simpleName}" }
            ?: "${result.decision}: aggregate evidence saved"
        metrics.text = "precision ${snapshot.precisionPermille}/1000  " +
            "recall ${snapshot.recallPermille}/1000  P95 ${snapshot.p95Millis} ms"
    }

    private fun finishWithError(error: Throwable) {
        start.isEnabled = true
        status.text = "Fixture preparation failed: ${error.javaClass.simpleName}"
    }

    override fun onDestroy() {
        generation += 1
        if (recognizerDelegate.isInitialized()) recognizer.close()
        super.onDestroy()
    }
}
