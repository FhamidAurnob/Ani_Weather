package com.example.wuwaweather.ui.sky

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.example.wuwaweather.data.Condition
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Code-drawn animated sky. Everything is generated at runtime, so it costs
 * no APK size and redraws at the display's frame rate.
 */
@Composable
fun LiveSky(condition: Condition, isDay: Boolean, modifier: Modifier = Modifier) {
    val palette = skyPaletteFor(condition, isDay)
    val spec = tween<Color>(durationMillis = 1200)
    val top by animateColorAsState(palette.top, spec, label = "skyTop")
    val mid by animateColorAsState(palette.mid, spec, label = "skyMid")
    val bottom by animateColorAsState(palette.bottom, spec, label = "skyBottom")

    val time = rememberSceneTime()
    val particles = remember { SkyParticles.create() }

    val showSun = isDay && (condition == Condition.CLEAR || condition == Condition.PARTLY_CLOUDY)
    val showNightSky = !isDay && (condition == Condition.CLEAR || condition == Condition.PARTLY_CLOUDY)
    val cloudCount = when (condition) {
        Condition.CLEAR -> 0
        Condition.PARTLY_CLOUDY -> 3
        Condition.FOG -> 2
        else -> 7
    }
    val stormy = condition in setOf(Condition.RAIN, Condition.THUNDER, Condition.DRIZZLE)

    Canvas(modifier.fillMaxSize()) {
        val t = time.value

        // 1. Sky gradient
        drawRect(Brush.verticalGradient(0f to top, 0.55f to mid, 1f to bottom))

        // 2. Sun / moon / stars
        if (showSun) drawSun(t)
        if (showNightSky) {
            drawStars(particles, t)
            drawMoon(t)
        }

        // 3. Clouds
        if (cloudCount > 0) {
            val cloudColor = when {
                !isDay -> Color(0xFF8C9AB8).copy(alpha = 0.16f)
                stormy -> Color(0xFF2B3442).copy(alpha = 0.45f)
                condition == Condition.SNOW -> Color(0xFFE9EEF5).copy(alpha = 0.35f)
                else -> Color.White.copy(alpha = 0.42f)
            }
            drawClouds(particles, t, cloudCount, cloudColor)
        }

        // 4. Precipitation & effects
        when (condition) {
            Condition.DRIZZLE -> drawRain(particles, t, count = 70, lengthScale = 0.6f, alpha = 0.35f)
            Condition.RAIN -> drawRain(particles, t, count = 170, lengthScale = 1f, alpha = 0.45f)
            Condition.THUNDER -> {
                drawRain(particles, t, count = 170, lengthScale = 1.1f, alpha = 0.45f)
                drawLightning(t)
            }
            Condition.SNOW -> drawSnow(particles, t)
            Condition.FOG -> drawFog(t, isDay)
            else -> Unit
        }

        // 5. Readability scrim at the bottom where the cards sit
        drawRect(
            Brush.verticalGradient(
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.28f)
            )
        )
    }
}

/** Seconds since this composable appeared, updated every frame. */
@Composable
private fun rememberSceneTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
        }
    }
    return time
}

/** Pre-generated random values so particles don't allocate per frame. */
private class SkyParticles(
    val rain: List<FloatArray>,   // x, phase, speed, length, width
    val snow: List<FloatArray>,   // x, phase, speed, radius, wobbleFreq, wobblePhase, alpha
    val stars: List<FloatArray>,  // x, y, radius, twinkleFreq, phase
    val clouds: List<FloatArray>  // startX, y, scale, speed
) {
    companion object {
        fun create(): SkyParticles {
            val r = Random(42)
            return SkyParticles(
                rain = List(200) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.9f + r.nextFloat() * 0.8f, 0.6f + r.nextFloat() * 0.6f, 0.8f + r.nextFloat() * 0.8f) },
                snow = List(120) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.04f + r.nextFloat() * 0.07f, 1.2f + r.nextFloat() * 2.8f, 0.4f + r.nextFloat() * 0.9f, r.nextFloat() * 6.28f, 0.45f + r.nextFloat() * 0.5f) },
                stars = List(140) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.7f, 0.4f + r.nextFloat() * 1.3f, 0.5f + r.nextFloat() * 2f, r.nextFloat() * 6.28f) },
                clouds = List(7) { i -> floatArrayOf(r.nextFloat() * 1.4f, 0.04f + (i % 4) * 0.075f + r.nextFloat() * 0.04f, 0.8f + r.nextFloat() * 0.7f, 0.006f + r.nextFloat() * 0.01f) }
            )
        }
    }
}

private fun frac(x: Float) = x - floor(x)

private fun DrawScope.drawSun(t: Float) {
    val c = Offset(size.width * 0.78f, size.height * 0.15f)
    val pulse = 1f + 0.04f * sin(t * 0.8f)
    // Wide atmospheric glow
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFFFE6A8).copy(alpha = 0.55f), Color(0xFFFFC56B).copy(alpha = 0.18f), Color.Transparent),
            center = c, radius = size.width * 0.75f * pulse
        ),
        radius = size.width * 0.75f * pulse, center = c
    )
    // Slowly rotating soft rays
    val rayLen = size.width * 0.32f
    for (i in 0 until 12) {
        val a = (i / 12f) * 2f * PI.toFloat() + t * 0.05f
        val end = Offset(c.x + kotlin.math.cos(a) * rayLen, c.y + sin(a) * rayLen)
        drawLine(Color.White.copy(alpha = 0.06f), c, end, strokeWidth = 18.dp.toPx(), cap = StrokeCap.Round)
    }
    // Disc
    val r = 38.dp.toPx() * pulse
    drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFF1C9), Color(0x00FFE3A0)), center = c, radius = r * 1.6f), r * 1.6f, c)
}

private fun DrawScope.drawMoon(t: Float) {
    val c = Offset(size.width * 0.76f, size.height * 0.15f)
    val glow = 1f + 0.05f * sin(t * 0.5f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFBFD0FF).copy(alpha = 0.30f), Color.Transparent), center = c, radius = size.width * 0.45f * glow),
        radius = size.width * 0.45f * glow, center = c
    )
    val r = 30.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFDF5), Color(0xFFE6E4DA)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.3f), r, c)
    // Subtle craters
    drawCircle(Color(0x14000000), r * 0.22f, c + Offset(r * 0.25f, r * 0.15f))
    drawCircle(Color(0x10000000), r * 0.14f, c + Offset(-r * 0.3f, r * 0.35f))
    drawCircle(Color(0x0D000000), r * 0.12f, c + Offset(-r * 0.05f, -r * 0.4f))
}

private fun DrawScope.drawStars(p: SkyParticles, t: Float) {
    for (s in p.stars) {
        val alpha = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * s[3] + s[4]))
        drawCircle(Color.White.copy(alpha = alpha), s[2].dp.toPx(), Offset(s[0] * size.width, s[1] * size.height))
    }
}

private fun DrawScope.drawClouds(p: SkyParticles, t: Float, count: Int, color: Color) {
    val w = size.width
    for (i in 0 until count) {
        val c = p.clouds[i]
        val span = 1.8f
        val x = (frac((c[0] + t * c[3]) / span) * span - 0.4f) * w
        val y = c[1] * size.height
        val s = c[2] * w * 0.22f
        // A cloud is a cluster of soft radial puffs
        puff(x, y, s, color)
        puff(x + s * 0.7f, y - s * 0.25f, s * 0.85f, color)
        puff(x + s * 1.35f, y, s * 0.9f, color)
        puff(x + s * 0.6f, y + s * 0.2f, s * 1.0f, color)
        puff(x - s * 0.55f, y + s * 0.1f, s * 0.7f, color)
    }
}

private fun DrawScope.puff(x: Float, y: Float, r: Float, color: Color) {
    val c = Offset(x, y)
    drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = color.alpha * 0.5f), Color.Transparent), center = c, radius = r), r, c)
}

private fun DrawScope.drawRain(p: SkyParticles, t: Float, count: Int, lengthScale: Float, alpha: Float) {
    val h = size.height
    val slant = 0.18f
    for (i in 0 until count) {
        val d = p.rain[i]
        val y = frac(d[1] + t * d[2] * 0.9f) * (h * 1.1f) - h * 0.05f
        val len = h * 0.035f * d[3] * lengthScale
        val x = d[0] * size.width * 1.1f - (y * slant * 0.15f)
        drawLine(
            color = Color(0xFFCFE6FF).copy(alpha = alpha * (0.5f + d[3] * 0.5f)),
            start = Offset(x, y),
            end = Offset(x - len * slant, y + len),
            strokeWidth = d[4].dp.toPx() * (if (lengthScale < 1f) 0.8f else 1f),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow(p: SkyParticles, t: Float) {
    for (f in p.snow) {
        val y = frac(f[1] + t * f[2]) * size.height * 1.05f - size.height * 0.02f
        val x = (f[0] + 0.03f * sin(t * f[4] + f[5])) * size.width
        val r = f[3].dp.toPx()
        drawCircle(Color.White.copy(alpha = f[6]), r, Offset(x, y))
    }
}

private fun DrawScope.drawLightning(t: Float) {
    val cycle = 7f
    val n = floor(t / cycle).toInt()
    val p = t - n * cycle
    val strength = when {
        p < 0.07f -> 0.55f
        p in 0.16f..0.22f -> 0.35f
        p in 0.30f..0.34f -> 0.2f
        else -> 0f
    }
    if (strength <= 0f) return
    drawRect(Color(0xFFE8E6FF).copy(alpha = strength * 0.45f))
    // A jagged bolt that changes position every strike
    val r = Random(n)
    val path = Path()
    var x = size.width * (0.2f + r.nextFloat() * 0.6f)
    var y = size.height * 0.05f
    path.moveTo(x, y)
    repeat(7) {
        x += (r.nextFloat() - 0.5f) * size.width * 0.12f
        y += size.height * (0.04f + r.nextFloat() * 0.035f)
        path.lineTo(x, y)
    }
    drawPath(path, Color.White.copy(alpha = strength + 0.3f), style = Stroke(width = 2.5f.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
    drawPath(path, Color(0xFFB9B4FF).copy(alpha = strength * 0.6f), style = Stroke(width = 10f.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
}

private fun DrawScope.drawFog(t: Float, isDay: Boolean) {
    val base = if (isDay) Color(0xFFEDE7DC) else Color(0xFF9AA0AE)
    for (i in 0 until 5) {
        val y = size.height * (0.25f + i * 0.15f)
        val x = size.width * (0.5f + 0.25f * sin(t * (0.05f + i * 0.015f) + i * 1.7f))
        val c = Offset(x, y)
        val r = size.width * 0.5f
        val col = base.copy(alpha = if (isDay) 0.28f else 0.14f)
        scale(scaleX = 2.6f, scaleY = 0.55f, pivot = c) {
            drawCircle(Brush.radialGradient(listOf(col, Color.Transparent), center = c, radius = r), r, c)
        }
    }
}
