package com.galleryorganizer.spikes.s02

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ResultClipboard(
    private val writeText: (String, String) -> Unit,
    private val readText: () -> String?,
) {
    constructor(writeText: (String, String) -> Unit) : this(writeText, { null })

    constructor(context: Context) : this(
        { label, text ->
            requireNotNull(context.getSystemService(ClipboardManager::class.java))
                .setPrimaryClip(ClipData.newPlainText(label, text))
        },
        {
            val manager = requireNotNull(context.getSystemService(ClipboardManager::class.java))
            manager.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(context)?.toString()
        },
    )

    fun copy(json: String): Boolean = runCatching { writeText(CLIP_LABEL, json) }.isSuccess
    fun read(): String? = runCatching {
        readText()?.trim()?.takeIf(String::isNotEmpty)
    }.getOrNull()

    private companion object {
        const val CLIP_LABEL = "s02-result"
    }
}
