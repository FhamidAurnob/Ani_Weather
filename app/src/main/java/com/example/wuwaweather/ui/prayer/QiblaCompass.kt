package com.example.wuwaweather.ui.prayer

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wuwaweather.i18n.Text
import com.example.wuwaweather.i18n.localDigits
import com.example.wuwaweather.i18n.tr
import com.example.wuwaweather.ui.components.GlassCard
import com.example.wuwaweather.ui.theme.Velora
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val KAABA_LAT = 21.4225
private const val KAABA_LON = 39.8262

/** Great-circle initial bearing from a place to the Kaaba, degrees from true north. */
fun qiblaBearing(lat: Double, lon: Double): Double {
    val p1 = Math.toRadians(lat)
    val p2 = Math.toRadians(KAABA_LAT)
    val dl = Math.toRadians(KAABA_LON - lon)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * kotlin.math.sin(p2) - kotlin.math.sin(p1) * cos(p2) * cos(dl)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

/** Compass that shows the Qibla direction. Uses the phone's rotation sensor, corrected to true north. */
@Composable
fun QiblaCard(lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val declination = remember(lat, lon) {
        GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
    }
    var heading by remember { mutableFloatStateOf(0f) }        // true-north heading of the phone
    var hasReading by remember { mutableStateOf(false) }
    var lowAccuracy by remember { mutableStateOf(false) }

    DisposableEffect(sensor) {
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        var smoothed = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                val raw = ((Math.toDegrees(orient[0].toDouble()).toFloat() + declination) + 360f) % 360f
                // Low-pass filter on the circle so the dial doesn't jitter
                smoothed = if (smoothed.isNaN()) raw else {
                    val d = ((raw - smoothed + 540f) % 360f) - 180f
                    (smoothed + d * 0.15f + 360f) % 360f
                }
                heading = smoothed
                hasReading = true
            }
            override fun onAccuracyChanged(s: Sensor, accuracy: Int) {
                lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        if (sensor != null) sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val qibla = remember(lat, lon) { qiblaBearing(lat, lon).toFloat() }
    val diff = ((qibla - heading + 540f) % 360f) - 180f           // -180..180, + means turn right
    val facing = hasReading && abs(diff) <= 4f
    LaunchedEffect(facing) { if (facing) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    val measurer = rememberTextMeasurer()
    GlassCard(modifier.fillMaxWidth(), title = tr("Qibla", "কিবলা")) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val r = size.minDimension / 2
                val c = center
                // Fixed pointer at the top = where the phone points
                val pointer = Path().apply {
                    moveTo(c.x, c.y - r - 2.dp.toPx())
                    lineTo(c.x - 8.dp.toPx(), c.y - r + 12.dp.toPx())
                    lineTo(c.x + 8.dp.toPx(), c.y - r + 12.dp.toPx())
                    close()
                }
                drawPath(pointer, if (facing) Velora.Accent else Color.White)

                // Dial rotates so North points to real north
                rotate(-heading, c) {
                    drawCircle(Color(0x33FFFFFF), r * 0.9f, c, style = Stroke(1.dp.toPx()))
                    for (i in 0 until 72) {
                        val a = Math.toRadians(i * 5.0 - 90).toFloat()
                        val major = i % 18 == 0
                        val inner = r * (if (major) 0.74f else if (i % 2 == 0) 0.8f else 0.84f)
                        drawLine(
                            Color.White.copy(alpha = if (major) 0.8f else 0.3f),
                            Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                            Offset(c.x + cos(a) * r * 0.88f, c.y + sin(a) * r * 0.88f),
                            strokeWidth = (if (major) 2 else 1).dp.toPx()
                        )
                    }
                    listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, deg) ->
                        val a = Math.toRadians(deg - 90.0).toFloat()
                        val layout = measurer.measure(
                            label,
                            TextStyle(color = if (label == "N") Velora.Accent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        )
                        val p = Offset(c.x + cos(a) * r * 0.6f, c.y + sin(a) * r * 0.6f)
                        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                    }
                    // Qibla needle with a small Kaaba block
                    val qa = Math.toRadians(qibla - 90.0).toFloat()
                    val tip = Offset(c.x + cos(qa) * r * 0.78f, c.y + sin(qa) * r * 0.78f)
                    drawLine(Velora.Accent, c, tip, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    val k = 9.dp.toPx()
                    drawRect(Color(0xFF1A1A1A), topLeft = Offset(tip.x - k / 2, tip.y - k / 2), size = androidx.compose.ui.geometry.Size(k, k))
                    drawRect(Velora.Accent, topLeft = Offset(tip.x - k / 2, tip.y - k / 2), size = androidx.compose.ui.geometry.Size(k, k * 0.3f))
                    drawCircle(Color.White, 4.dp.toPx(), c)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val bearing = "${qibla.roundToInt()}°"
        Text(
            when {
                sensor == null -> tr("No compass on this phone. Qibla is $bearing from North.", "এই ফোনে কম্পাস নেই। কিবলা উত্তর থেকে $bearing।")
                !hasReading -> tr("Hold your phone flat…", "ফোনটি সমতলে ধরুন…")
                facing -> "✓ " + tr("You're facing the Qibla", "আপনি কিবলার দিকে মুখ করে আছেন")
                diff > 0 -> tr("Turn right ${diff.roundToInt()}°", "ডানে ${diff.roundToInt()}° ঘুরুন")
                else -> tr("Turn left ${(-diff).roundToInt()}°", "বামে ${(-diff).roundToInt()}° ঘুরুন")
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (facing) Velora.Accent else Velora.TextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            (tr("Qibla $bearing from true North", "প্রকৃত উত্তর থেকে কিবলা $bearing") +
                if (lowAccuracy) tr(" · Move your phone in a figure-8 to calibrate", " · ক্যালিব্রেট করতে ফোনটি ৮-এর মতো ঘোরান") else "").localDigits(),
            style = MaterialTheme.typography.labelSmall,
            color = Velora.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
