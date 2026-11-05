package com.galleryorganizer.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ViewerBackground = Color(0xFF111210)
private val ViewerForeground = Color(0xFFF4F5F1)
private val ViewerMuted = Color(0xFFB8BBB2)

@Composable
internal fun ImageViewerScreen(
    state: ImageViewerUiState,
    preview: ImageBitmap?,
    onEvent: (ImageViewerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.currentItem ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onEvent(ImageViewerEvent.Close) }) {
                Text("X", color = ViewerForeground, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    color = ViewerForeground,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = "${state.positionLabel} · bounded preview",
                    color = ViewerMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(
                onClick = { onEvent(ImageViewerEvent.ToggleZoom) },
                enabled = state.loadState == ImageViewerLoadState.READY,
            ) {
                Text(
                    text = if (state.zoom == ImageViewerZoom.FIT) "2x" else "1x",
                    color = if (state.loadState == ImageViewerLoadState.READY) {
                        ViewerForeground
                    } else {
                        ViewerMuted
                    },
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    enabled = state.loadState == ImageViewerLoadState.READY,
                    onClick = { onEvent(ImageViewerEvent.ToggleZoom) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                preview != null && state.loadState == ImageViewerLoadState.READY -> Image(
                    bitmap = preview,
                    contentDescription = item.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = if (state.zoom == ImageViewerZoom.DETAIL) 2f else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                    contentScale = ContentScale.Fit,
                )
                state.loadState == ImageViewerLoadState.UNAVAILABLE -> ViewerStatus(
                    "Preview unavailable",
                )
                else -> ViewerStatus("Loading preview...")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onEvent(ImageViewerEvent.Previous) },
                enabled = state.canGoPrevious,
            ) {
                Text("<", color = if (state.canGoPrevious) ViewerForeground else ViewerMuted)
            }
            Text(item.detail, color = ViewerMuted, style = MaterialTheme.typography.bodySmall)
            IconButton(
                onClick = { onEvent(ImageViewerEvent.Next) },
                enabled = state.canGoNext,
            ) {
                Text(">", color = if (state.canGoNext) ViewerForeground else ViewerMuted)
            }
        }
    }
}

@Composable
private fun ViewerStatus(message: String) {
    Text(
        text = message,
        color = ViewerMuted,
        style = MaterialTheme.typography.bodyMedium,
    )
}
