package com.example.wuwaweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wuwaweather.data.AirQuality
import com.example.wuwaweather.data.CurrentWeather
import com.example.wuwaweather.data.UnitSystem
import com.example.wuwaweather.data.UvInfo
import com.example.wuwaweather.data.WeatherBundle
import com.example.wuwaweather.data.WeatherRepository
import com.example.wuwaweather.ui.theme.Velora
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Two-column grid of detail tiles + the sun arc card. */
@Composable
fun DetailsSection(bundle: WeatherBundle, modifier: Modifier = Modifier) {
    val c = bundle.current
    val units = bundle.units
    val gap = 12.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        if (bundle.air != null || bundle.uv != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (bundle.air != null) AirQualityTile(bundle.air, Modifier.weight(1f))
                if (bundle.uv != null) UvTile(bundle.uv, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            WindTile(c.windSpeed, c.windDeg, units, Modifier.weight(1f))
            FeelsLikeTile(c.feelsLike, c.temp, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            HumidityTile(c.humidity, Modifier.weight(1f))
            PressureTile(c.pressure, Modifier.weight(1f))
        }
        SunCard(c)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            val km = if (units == UnitSystem.IMPERIAL) c.visibility / 0.621371 else c.visibility
            SimpleTile("Visibility", formatDistance(c.visibility, units), visibilityNote(km), Modifier.weight(1f))
            SimpleTile("Cloud cover", "${c.cloudiness}%", cloudNote(c.cloudiness), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(title: String, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    GlassCard(modifier.aspectRatio(1f), title = title) {
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun BigValue(value: String, unit: String = "") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp), color = Velora.TextPrimary)
        if (unit.isNotEmpty()) {
            Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = Velora.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Velora.TextSecondary, modifier = modifier)
}

@Composable
private fun WindTile(speed: Double, deg: Int, units: UnitSystem, modifier: Modifier) {
    Tile("Wind", modifier) {
        Column(Modifier.align(Alignment.TopStart)) {
            BigValue(String.format(Locale.getDefault(), "%.1f", speed), units.wind)
            Note("from ${compassPoint(deg)}")
        }
        Canvas(Modifier.size(54.dp).align(Alignment.BottomEnd)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(Color(0x33FFFFFF), r, c, style = Stroke(width = 1.dp.toPx()))
            // ticks
            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0).toFloat()
                val inner = if (i % 9 == 0) r * 0.72f else r * 0.84f
                drawLine(
                    Color.White.copy(alpha = if (i % 9 == 0) 0.7f else 0.25f),
                    Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                    Offset(c.x + cos(a) * r * 0.95f, c.y + sin(a) * r * 0.95f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            // Arrow points where the wind is blowing TO (meteorological deg is where it comes FROM)
            rotate(degrees = deg + 180f, pivot = c) {
                val arrow = Path().apply {
                    moveTo(c.x, c.y - r * 0.68f)
                    lineTo(c.x - r * 0.16f, c.y - r * 0.36f)
                    lineTo(c.x + r * 0.16f, c.y - r * 0.36f)
                    close()
                }
                drawLine(Color.White, Offset(c.x, c.y + r * 0.6f), Offset(c.x, c.y - r * 0.4f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawPath(arrow, Velora.Accent)
            }
            drawCircle(Color.White, 3.dp.toPx(), c)
        }
    }
}

@Composable
private fun FeelsLikeTile(feels: Int, actual: Int, modifier: Modifier) {
    Tile("Feels like", modifier) {
        Column {
            BigValue("$feels°")
            Spacer(Modifier.height(6.dp))
            Note(
                when {
                    feels >= actual + 2 -> "Humidity makes it feel warmer."
                    feels <= actual - 2 -> "Wind makes it feel cooler."
                    else -> "Similar to the actual temperature."
                }
            )
        }
    }
}

@Composable
private fun HumidityTile(humidity: Int, modifier: Modifier) {
    Tile("Humidity", modifier) {
        Column(Modifier.align(Alignment.TopStart)) {
            BigValue("$humidity%")
            Note(
                when {
                    humidity >= 80 -> "Very humid"
                    humidity >= 60 -> "Humid"
                    humidity >= 35 -> "Comfortable"
                    else -> "Dry"
                }
            )
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp).align(Alignment.BottomStart)) {
            val h = size.height
            drawRoundRect(Color(0x26FFFFFF), size = size, cornerRadius = CornerRadius(h / 2))
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF7FD1FF), Color(0xFF3FA2FF))),
                size = Size(size.width * humidity / 100f, h),
                cornerRadius = CornerRadius(h / 2)
            )
        }
    }
}

@Composable
private fun PressureTile(pressure: Int, modifier: Modifier) {
    Tile("Pressure", modifier) {
        Column(Modifier.align(Alignment.TopStart)) {
            BigValue("$pressure", "hPa")
            Note(
                when {
                    pressure < 1000 -> "Low"
                    pressure > 1025 -> "High"
                    else -> "Normal"
                }
            )
        }
        // Gauge from 960 to 1060 hPa
        Canvas(Modifier.size(54.dp).align(Alignment.BottomEnd)) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Color(0x26FFFFFF), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val frac = ((pressure - 960) / 100f).coerceIn(0f, 1f)
            drawArc(Velora.Accent, 135f, 270f * frac, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun SimpleTile(title: String, value: String, note: String, modifier: Modifier) {
    Tile(title, modifier) {
        Column {
            BigValue(value)
            Spacer(Modifier.height(4.dp))
            Note(note)
        }
    }
}

/** Arc showing the sun's progress between sunrise and sunset, in the city's local time. */
@Composable
private fun SunCard(c: CurrentWeather, modifier: Modifier = Modifier) {
    val tz = c.tzOffsetSeconds
    val nowSec = System.currentTimeMillis() / 1000
    val progress = ((nowSec - c.sunrise).toFloat() / (c.sunset - c.sunrise).coerceAtLeast(1)).coerceIn(0f, 1f)
    val isUp = nowSec in c.sunrise until c.sunset

    GlassCard(modifier.fillMaxWidth(), title = "Sun") {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val w = size.width
            val h = size.height
            val left = Offset(w * 0.06f, h * 0.92f)
            val right = Offset(w * 0.94f, h * 0.92f)
            val peakY = h * 0.05f
            // Quadratic curve: point(t) = (1-t)^2*L + 2(1-t)t*P + t^2*R, with control point P
            val control = Offset(w / 2, peakY * 2 - left.y) // makes the curve peak at peakY
            fun point(t: Float): Offset {
                val u = 1 - t
                return Offset(
                    u * u * left.x + 2 * u * t * control.x + t * t * right.x,
                    u * u * left.y + 2 * u * t * control.y + t * t * right.y
                )
            }
            // horizon
            drawLine(Color(0x33FFFFFF), Offset(0f, left.y), Offset(w, left.y), strokeWidth = 1.dp.toPx())
            // full path (dashed)
            val full = Path().apply {
                moveTo(left.x, left.y)
                quadraticBezierTo(control.x, control.y, right.x, right.y)
            }
            drawPath(full, Color(0x40FFFFFF), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))))
            // travelled part
            val travelled = Path().apply {
                moveTo(left.x, left.y)
                val steps = 40
                for (i in 1..steps) {
                    val p = point(progress * i / steps)
                    lineTo(p.x, p.y)
                }
            }
            drawPath(travelled, Velora.Accent.copy(alpha = 0.9f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            // sun
            val sun = point(progress)
            if (isUp) {
                drawCircle(Brush.radialGradient(listOf(Velora.Accent.copy(alpha = 0.6f), Color.Transparent), center = sun, radius = 22.dp.toPx()), 22.dp.toPx(), sun)
                drawCircle(Color(0xFFFFF4D6), 7.dp.toPx(), sun)
            } else {
                drawCircle(Color(0x66FFFFFF), 5.dp.toPx(), sun)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SunTime("Sunrise", WeatherRepository.format(c.sunrise, tz, "HH:mm"))
            SunTime("Sunset", WeatherRepository.format(c.sunset, tz, "HH:mm"), alignEnd = true)
        }
    }
}

@Composable
private fun SunTime(label: String, time: String, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary)
        Text(time, style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

private fun compassPoint(deg: Int): String {
    val points = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[(((deg % 360) + 360) % 360 / 45f + 0.5f).toInt() % 8]
}

private fun formatDistance(value: Double, units: UnitSystem): String =
    if (value >= 10) "${value.roundToInt()} ${units.distance}"
    else String.format(Locale.getDefault(), "%.1f %s", value, units.distance)

// ---------- Air quality & UV ----------

private val aqiLabels = listOf("Good", "Fair", "Moderate", "Poor", "Very poor")
private val aqiColors = listOf(Color(0xFF6BD68A), Color(0xFFC8E06A), Color(0xFFFFD36B), Color(0xFFFF9B5E), Color(0xFFE5677A))

@Composable
private fun AirQualityTile(air: AirQuality, modifier: Modifier) {
    val idx = (air.index - 1).coerceIn(0, 4)
    Tile("Air quality", modifier) {
        Column(Modifier.align(Alignment.TopStart)) {
            BigValue(aqiLabels[idx])
            Note(air.pm25?.let { "PM2.5 · ${it.roundToInt()} µg/m³" } ?: "Index ${air.index} of 5")
        }
        SegmentBar(
            colors = aqiColors,
            position = (idx + 0.5f) / 5f,
            modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun UvTile(uv: UvInfo, modifier: Modifier) {
    val value = uv.now.roundToInt()
    val label = when {
        value <= 2 -> "Low"
        value <= 5 -> "Moderate"
        value <= 7 -> "High"
        value <= 10 -> "Very high"
        else -> "Extreme"
    }
    Tile("UV index", modifier) {
        Column(Modifier.align(Alignment.TopStart)) {
            BigValue("$value", label)
            Note(uv.todayMax?.let { "Today's max: ${it.roundToInt()}" } ?: "")
        }
        SegmentBar(
            colors = listOf(Color(0xFF6BD68A), Color(0xFFFFD36B), Color(0xFFFF9B5E), Color(0xFFE5677A), Color(0xFFB07CFF)),
            position = (uv.now / 11.0).toFloat().coerceIn(0.02f, 0.98f),
            modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.BottomStart)
        )
    }
}

/** A smooth multicolour track with a white marker at [position] (0..1). */
@Composable
private fun SegmentBar(colors: List<Color>, position: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val h = size.height
        drawRoundRect(Brush.horizontalGradient(colors), size = size, cornerRadius = CornerRadius(h / 2), alpha = 0.9f)
        val x = (size.width * position).coerceIn(h / 2, size.width - h / 2)
        drawCircle(Color(0xFF0A0F24), radius = h * 0.85f, center = Offset(x, h / 2))
        drawCircle(Color.White, radius = h * 0.6f, center = Offset(x, h / 2))
    }
}

private fun visibilityNote(km: Double) = when {
    km >= 10 -> "Perfectly clear view."
    km >= 5 -> "Good visibility."
    km >= 2 -> "Haze is reducing visibility."
    else -> "Low visibility. Take care."
}

private fun cloudNote(pct: Int) = when {
    pct < 15 -> "Clear skies."
    pct < 50 -> "A few clouds."
    pct < 85 -> "Mostly cloudy."
    else -> "Overcast."
}
