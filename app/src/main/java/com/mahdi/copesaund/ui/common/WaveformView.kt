package com.mahdi.copesaund.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WaveformView(
    amplitudes: List<Int>,
    modifier: Modifier = Modifier,
    progress: Float = -1f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    idleColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val centerY = size.height / 2
        val maxBar = size.height * 0.48f
        if (amplitudes.isEmpty()) {
            var x = 0f
            while (x < size.width) {
                drawRoundRect(
                    color = idleColor,
                    topLeft = Offset(x, centerY - 2.dp.toPx()),
                    size = Size(barWidth, 4.dp.toPx()),
                    cornerRadius = CornerRadius(barWidth)
                )
                x += barWidth + gap
            }
            return@Canvas
        }
        val step = barWidth + gap
        val coloredBars = (progress.coerceIn(0f, 1f) * amplitudes.size).toInt()
        amplitudes.forEachIndexed { i, value ->
            val v = (value / 100f).coerceIn(0.02f, 1f)
            val barH = (v * maxBar).coerceAtLeast(4.dp.toPx())
            val x = i * step
            if (x > size.width) return@forEachIndexed
            drawRoundRect(
                color = if (progress < 0f || i <= coloredBars) activeColor else idleColor,
                topLeft = Offset(x, centerY - barH / 2),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth)
            )
        }
    }
}
