package com.example.wuwaweather.ui.prayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wuwaweather.alerts.AlarmScheduler
import com.example.wuwaweather.alerts.Notifications
import com.example.wuwaweather.calendar.Hijri
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.SettingsData
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.prayer.Madhab
import com.example.wuwaweather.prayer.Prayer
import com.example.wuwaweather.prayer.PrayerCalculator
import com.example.wuwaweather.prayer.PrayerMethod
import com.example.wuwaweather.ui.FeatureScreen
import com.example.wuwaweather.ui.Pill
import com.example.wuwaweather.ui.PrimaryButton
import com.example.wuwaweather.ui.components.GlassCard
import com.example.wuwaweather.ui.theme.Manrope
import com.example.wuwaweather.ui.theme.Velora
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun PrayerScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val home by HomeStore.home.collectAsStateWithLifecycle()
    val settingsFlow = remember { VeloraSettings.flow(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle()
    val s = settings ?: SettingsData()
    LaunchedEffect(Unit) { HomeStore.get(context) }

    // Ticks every second for the countdown
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun update(transform: (SettingsData) -> SettingsData) {
        VeloraSettings.update(context, transform)
        AlarmScheduler.rescheduleAll(context)
    }
    fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val h = home
    if (h == null) {
        FeatureScreen("Prayer times", null, bottomPadding) {
            item { GlassCard(Modifier.fillMaxWidth()) { Text("Open the Weather tab once so Velora knows your location.", color = Velora.TextPrimary) } }
        }
        return
    }

    val zone = ZoneOffset.ofTotalSeconds(h.tzOffsetSeconds)
    val today = java.time.Instant.ofEpochMilli(now).atOffset(zone).toLocalDate()
    val day = remember(today, h, s.method, s.madhab) { PrayerCalculator.calculate(today, h.lat, h.lon, h.tzOffsetSeconds, s.method, s.madhab) }
    val tomorrow = remember(today, h, s.method, s.madhab) { PrayerCalculator.calculate(today.plusDays(1), h.lat, h.lon, h.tzOffsetSeconds, s.method, s.madhab) }
    val hijri = remember(today, s.hijriOffset) { runCatching { Hijri.of(today, s.hijriOffset) }.getOrNull() }

    // Next prayer (Sunrise is shown but not counted as a prayer)
    val order = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
    val next = order.firstOrNull { day[it] > now }?.let { it to day[it] } ?: (Prayer.FAJR to tomorrow[Prayer.FAJR])
    val current = order.lastOrNull { day[it] <= now }

    FeatureScreen(
        title = "Prayer times",
        subtitle = listOfNotNull(h.name, hijri?.toString(), today.format(DateTimeFormatter.ofPattern("d MMM yyyy"))).joinToString(" · "),
        bottomPadding = bottomPadding
    ) {
        // Next prayer hero
        item(key = "next") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("NEXT PRAYER", style = MaterialTheme.typography.labelMedium, color = Velora.TextTertiary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(next.first.label, fontFamily = Manrope, fontWeight = FontWeight.ExtraLight, fontSize = 52.sp, color = Velora.TextPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        AlarmScheduler.formatTime(next.second, h.tzOffsetSeconds),
                        style = MaterialTheme.typography.titleLarge, color = Velora.Accent,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Text("in ${countdown(next.second - now)}", style = MaterialTheme.typography.bodyLarge, color = Velora.TextSecondary)
            }
        }

        // Today's list with alert switches
        item(key = "list") {
            GlassCard(Modifier.fillMaxWidth(), title = "Today") {
                Prayer.values().forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = Velora.Divider, thickness = 0.5.dp)
                    val isCurrent = p == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) Color(0x14FFD27A) else Color.Transparent)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (p == Prayer.SUNRISE) Velora.TextSecondary else Velora.TextPrimary,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isCurrent) Text("Now", style = MaterialTheme.typography.labelSmall, color = Velora.Accent)
                        }
                        Text(
                            AlarmScheduler.formatTime(day[p], h.tzOffsetSeconds),
                            style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = p in s.prayerAlerts,
                            onCheckedChange = { on ->
                                if (on) ensureNotifications()
                                update { it.copy(prayerAlerts = if (on) it.prayerAlerts + p else it.prayerAlerts - p) }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Velora.Accent, checkedThumbColor = Color(0xFF1A1A1A))
                        )
                    }
                }
            }
        }

        // Exact-alarm permission hint (Android 12+)
        if (s.prayerAlerts.isNotEmpty() && !AlarmScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= 31) {
            item(key = "exact") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("For on-time alerts, allow Velora to set alarms.", color = Velora.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text("Without it, Android may deliver alerts a few minutes late.", color = Velora.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Allow alarms & reminders", {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    })
                }
            }
        }

        // Calculation settings
        item(key = "settings") {
            GlassCard(Modifier.fillMaxWidth(), title = "Calculation") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerMethod.values().forEach { m -> Pill(m.label, selected = s.method == m) { update { it.copy(method = m) } } }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Madhab.values().forEach { m -> Pill("Asr: ${m.label}", selected = s.madhab == m) { update { it.copy(madhab = m) } } }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Times are calculated on your phone for ${h.name} and work offline.",
                    style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary
                )
            }
        }
    }
}

private fun countdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hh = total / 3600
    val mm = (total % 3600) / 60
    val ss = total % 60
    return if (hh > 0) "${hh}h ${mm}m" else "${mm}m ${ss}s"
}
