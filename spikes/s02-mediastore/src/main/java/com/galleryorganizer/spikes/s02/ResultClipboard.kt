package com.galleryorganizer.spikes.s02

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ResultClipboard(private val writeText: (String, String) -> Unit) {
    constructor(context: Context) : this(
        { label, text ->
            requireNotNull(context.getSystemService(ClipboardManager::class.java))
                .setPrimaryClip(ClipData.newPlainText(label, text))
        },
    )

    fun copy(json: String): Boolean = runCatching { writeText(CLIP_LABEL, json) }.isSuccess

    private companion object {
        const val CLIP_LABEL = "s02-result"
    }
}
