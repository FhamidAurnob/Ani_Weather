package com.example.wuwaweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.example.wuwaweather.i18n.Text
import com.example.wuwaweather.i18n.tr
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.wuwaweather.data.DailyItem
import com.example.wuwaweather.data.HourlyItem
import com.example.wuwaweather.ui.theme.Velora

@Composable
fun HourlyCard(items: List<HourlyItem>, modifier: Modifier = Modifier) {
    GlassCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp)) {
        SectionLabel(tr("Next 24 hours", "আগামী ২৪ ঘণ্টা"), Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
        ) {
            itemsIndexed(items) { index, item ->
                Column(
                    Modifier.width(58.dp).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index == 0) Velora.TextPrimary else Velora.TextSecondary,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold
                    )
                    WeatherGlyph(item.condition, item.isDay, size = 30.dp)
                    Text(
                        if (item.popPercent >= 20) "${item.popPercent}%" else " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Velora.Rain
                    )
                    Text("${item.temp}°", style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary)
                }
            }
        }
    }
}

@Composable
fun DailyCard(items: List<DailyItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val lowest = items.minOf { it.min }
    val highest = items.maxOf { it.max }
    GlassCard(modifier, title = tr("5-day forecast", "৫ দিনের পূর্বাভাস")) {
        items.forEachIndexed { i, day ->
            if (i > 0) HorizontalDivider(color = Velora.Divider, thickness = 0.5.dp)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    day.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Velora.TextPrimary,
                    modifier = Modifier.width(64.dp)
                )
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherGlyph(day.condition, isDay = true, size = 26.dp)
                    if (day.popPercent >= 20) {
                        Text("${day.popPercent}%", style = MaterialTheme.typography.labelSmall, color = Velora.Rain)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${day.min}°",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Velora.TextTertiary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(38.dp)
                )
                TempRangeBar(
                    min = day.min, max = day.max, lowest = lowest, highest = highest,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                Text(
                    "${day.max}°",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Velora.TextPrimary,
                    modifier = Modifier.width(38.dp)
                )
            }
        }
    }
}

/** A track showing where this day's range sits within the whole week's range. */
@Composable
private fun TempRangeBar(min: Int, max: Int, lowest: Int, highest: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.height(6.dp)) {
        val h = size.height
        drawRoundRect(Color(0x26FFFFFF), size = size, cornerRadius = CornerRadius(h / 2))
        val span = (highest - lowest).coerceAtLeast(1).toFloat()
        val start = (min - lowest) / span * size.width
        val end = ((max - lowest) / span * size.width).coerceAtLeast(start + h)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF7FD1FF), Color(0xFFB6E3A8), Velora.Accent, Color(0xFFFF9F6B)),
                startX = 0f, endX = size.width
            ),
            topLeft = Offset(start, 0f),
            size = Size(end - start, h),
            cornerRadius = CornerRadius(h / 2)
        )
    }
}

/** "Next 2 hours" rain nowcast: summary line plus 15-minute bars. */
@Composable
fun NowcastCard(nowcast: com.example.wuwaweather.data.Nowcast, modifier: Modifier = Modifier) {
    val maxMm = maxOf(0.6, nowcast.slots.maxOf { it.precipMm })
    val dry = nowcast.slots.all { it.precipMm < 0.1 }
    GlassCard(modifier.fillMaxWidth(), title = com.example.wuwaweather.i18n.tr("Next 2 hours", "আগামী ২ ঘণ্টা")) {
        Text(nowcast.summary, style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary)
        if (!dry) {
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val n = nowcast.slots.size
                val gap = 6.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                nowcast.slots.forEachIndexed { i, s ->
                    val frac = (s.precipMm / maxMm).toFloat().coerceIn(0f, 1f)
                    val h = maxOf(3.dp.toPx(), size.height * frac)
                    drawRoundRect(
                        color = if (s.precipMm >= 0.1) Velora.Rain else Color(0x26FFFFFF),
                        topLeft = Offset(i * (w + gap), size.height - h),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(
                    com.example.wuwaweather.i18n.tr("Now", "এখন"), "30m", "1h", "1.5h", "2h"
                ).forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary) }
            }
        }
    }
}
