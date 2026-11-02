package com.example.wuwaweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wuwaweather.data.Condition
import com.example.wuwaweather.ui.theme.Velora
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Minimal, consistent weather icons drawn in code (no image assets). */
@Composable
fun WeatherGlyph(condition: Condition, isDay: Boolean, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (condition) {
            Condition.CLEAR -> if (isDay) sun(Offset(s * 0.5f, s * 0.5f), s * 0.2f) else moon(Offset(s * 0.5f, s * 0.5f), s * 0.3f)
            Condition.PARTLY_CLOUDY -> {
                if (isDay) sun(Offset(s * 0.36f, s * 0.36f), s * 0.15f) else moon(Offset(s * 0.38f, s * 0.36f), s * 0.22f)
                cloud(Offset(s * 0.56f, s * 0.62f), s * 0.8f)
            }
            Condition.CLOUDS -> cloud(Offset(s * 0.5f, s * 0.52f), s)
            Condition.DRIZZLE -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.9f)
                drops(s, count = 2, lengthFactor = 0.1f)
            }
            Condition.RAIN -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.9f)
                drops(s, count = 3, lengthFactor = 0.18f)
            }
            Condition.THUNDER -> {
                cloud(Offset(s * 0.5f, s * 0.38f), s * 0.9f, Color(0xFFD5D9E3))
                bolt(s)
            }
            Condition.SNOW -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.9f)
                for (i in 0 until 3) drawCircle(Color.White, s * 0.045f, Offset(s * (0.3f + i * 0.2f), s * (0.8f + (i % 2) * 0.08f)))
            }
            Condition.FOG -> {
                for (i in 0 until 3) {
                    val y = s * (0.32f + i * 0.18f)
                    val inset = if (i == 1) 0.12f else 0.2f
                    drawLine(Color.White.copy(alpha = 0.9f - i * 0.15f), Offset(s * inset, y), Offset(s * (1 - inset), y), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float) {
    drawCircle(Velora.Accent, r, c)
    for (i in 0 until 8) {
        val a = i * (PI.toFloat() / 4f)
        val start = Offset(c.x + cos(a) * r * 1.5f, c.y + sin(a) * r * 1.5f)
        val end = Offset(c.x + cos(a) * r * 2.1f, c.y + sin(a) * r * 2.1f)
        drawLine(Velora.Accent, start, end, strokeWidth = r * 0.35f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val full = Path().apply { addOval(Rect(c, r)) }
    val cut = Path().apply { addOval(Rect(Offset(c.x + r * 0.55f, c.y - r * 0.35f), r * 0.85f)) }
    val crescent = Path().apply { op(full, cut, PathOperation.Difference) }
    drawPath(crescent, Color(0xFFF3EFD9))
}

private fun DrawScope.cloud(center: Offset, width: Float, color: Color = Color.White) {
    val w = width * 0.8f
    val baseY = center.y + w * 0.18f
    drawCircle(color, w * 0.22f, Offset(center.x - w * 0.18f, baseY - w * 0.12f))
    drawCircle(color, w * 0.28f, Offset(center.x + w * 0.08f, baseY - w * 0.2f))
    drawCircle(color, w * 0.18f, Offset(center.x + w * 0.32f, baseY - w * 0.06f))
    drawRoundRect(
        color,
        topLeft = Offset(center.x - w * 0.42f, baseY - w * 0.14f),
        size = androidx.compose.ui.geometry.Size(w * 0.9f, w * 0.26f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.13f)
    )
}

private fun DrawScope.drops(s: Float, count: Int, lengthFactor: Float) {
    for (i in 0 until count) {
        val x = s * (0.32f + i * (0.36f / (count - 1).coerceAtLeast(1)))
        val y = s * 0.7f
        drawLine(Velora.Rain, Offset(x, y), Offset(x - s * 0.05f, y + s * lengthFactor), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.bolt(s: Float) {
    val p = Path().apply {
        moveTo(s * 0.54f, s * 0.58f)
        lineTo(s * 0.4f, s * 0.8f)
        lineTo(s * 0.52f, s * 0.8f)
        lineTo(s * 0.44f, s * 0.98f)
        lineTo(s * 0.66f, s * 0.72f)
        lineTo(s * 0.54f, s * 0.72f)
        lineTo(s * 0.62f, s * 0.58f)
        close()
    }
    drawPath(p, Velora.Accent)
}
