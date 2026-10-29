package com.galleryorganizer.spikes.s05.harness

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.galleryorganizer.spikes.s05.ThumbnailBenchmarkBudget
import com.galleryorganizer.spikes.s05.ThumbnailBenchmarkClassifier
import com.galleryorganizer.spikes.s05.ThumbnailBenchmarkRecorder
import java.io.File
import kotlin.concurrent.thread

class ThumbnailHarnessActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var grid: GridView
    private lateinit var copy: Button
    private var lastResultJson: String? = null
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { text = "Ready: app-private fixtures only" }
        grid = GridView(this).apply {
            numColumns = 3
            verticalSpacing = 4
            horizontalSpacing = 4
        }
        val start = Button(this).apply {
            text = "Run benchmark"
            setOnClickListener {
                isEnabled = false
                copy.isEnabled = false
                status.text = "Preparing controlled fixtures..."
                startRun()
            }
        }
        copy = Button(this).apply {
            text = "Copy result JSON"
            isEnabled = false
            setOnClickListener {
                val json = lastResultJson ?: return@setOnClickListener
                status.text = if (ResultClipboard(this@ThumbnailHarnessActivity).copy(json)) {
                    "Result JSON copied to clipboard"
                } else {
                    "Clipboard unavailable"
                }
            }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                addView(start)
                addView(copy)
                addView(status)
                addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
            },
        )
    }

    private fun startRun() {
        thread(name = "s05-fixture-prepare") {
            val store = AppPrivateFixtureStore(this)
            val provenance = store.prepare(FixtureSpec(count = 96, edgePx = 256, seed = 520))
            runOnUiThread { runScroll(store, provenance) }
        }
    }

    private fun runScroll(store: AppPrivateFixtureStore, provenance: FixtureProvenance) {
        val recorder = ThumbnailBenchmarkRecorder()
        val frames = FrameIntervalAdapter(recorder, maximumSamples = 360)
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (running && frames.onFrame(frameTimeNanos)) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        running = true
        grid.adapter = ThumbnailGridAdapter(
            store.files,
            AppPrivateThumbnailFetcher(recorder, active = { running }),
        )
        Choreographer.getInstance().postFrameCallback(callback)
        var toEnd = true
        val scroll = object : Runnable {
            override fun run() {
                if (!running) return
                grid.smoothScrollToPosition(if (toEnd) store.files.lastIndex else 0)
                toEnd = !toEnd
                handler.postDelayed(this, 1_000)
            }
        }
        handler.post(scroll)
        handler.postDelayed({
            running = false
            handler.removeCallbacks(scroll)
            Choreographer.getInstance().removeFrameCallback(callback)
            grid.smoothScrollBy(0, 0)
            recorder.complete()
            publish(provenance, recorder)
        }, 10_000)
    }

    private fun publish(provenance: FixtureProvenance, recorder: ThumbnailBenchmarkRecorder) {
        val metrics = recorder.snapshot()
        val result = ThumbnailBenchmarkClassifier.classify(
            metrics,
            ThumbnailBenchmarkBudget(300, 96, 256L * 1024 * 1024, 24, 20, 0),
        )
        val json = BenchmarkEvidence(
            device = readDeviceProvenance(),
            fixture = provenance,
            frameSamples = metrics.frameSampleCount,
            fetchSamples = metrics.fetchSampleCount,
            failedFetches = metrics.failedFetchCount,
            peakHeapBytes = metrics.peakHeapBytes,
            frameP95Millis = metrics.frameP95Millis,
            fetchP95Millis = metrics.fetchP95Millis,
            oomObserved = metrics.oomObserved,
            decision = result.decision.name,
            violations = result.violations.map { it.name },
        ).toJson()
        val output = File(filesDir, "s05-results/latest.json")
        val writeFailure = runCatching {
            check(output.parentFile?.mkdirs() == true || output.parentFile?.isDirectory == true)
            output.writeText(json.toString(2))
        }.exceptionOrNull()
        lastResultJson = json.toString()
        copy.isEnabled = true
        status.text = "${result.decision}: ${metrics.frameSampleCount} frames, " +
            "${metrics.fetchSampleCount} fetches\n" +
            "${json.optString("deviceManufacturer")} ${json.optString("deviceModel")} " +
            "API ${json.optInt("deviceApiLevel")}\n" +
            (writeFailure?.let { "Result write failed: ${it.javaClass.simpleName}" }
                ?: output.absolutePath)
    }
}

private class ThumbnailGridAdapter(
    private val files: List<File>,
    private val fetcher: AppPrivateThumbnailFetcher,
) : BaseAdapter() {
    override fun getCount() = files.size
    override fun getItem(position: Int) = files[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val image = (convertView as? ImageView) ?: ImageView(parent.context).apply {
            layoutParams = AbsListView.LayoutParams(256, 256)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.LTGRAY)
        }
        image.setImageBitmap(fetcher.fetch(files[position]))
        return image
    }
}
