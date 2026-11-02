package com.example.wuwaweather.ui.prayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wuwaweather.alerts.AdhanService
import com.example.wuwaweather.alerts.AlarmScheduler
import com.example.wuwaweather.alerts.Notifications
import com.example.wuwaweather.calendar.Bangabda
import com.example.wuwaweather.calendar.Hijri
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.SettingsData
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.i18n.I18n
import com.example.wuwaweather.i18n.Text
import com.example.wuwaweather.i18n.tr
import com.example.wuwaweather.prayer.Madhab
import com.example.wuwaweather.prayer.Prayer
import com.example.wuwaweather.prayer.PrayerCalculator
import com.example.wuwaweather.prayer.PrayerMethod
import com.example.wuwaweather.ui.FeatureScreen
import com.example.wuwaweather.ui.Pill
import com.example.wuwaweather.ui.PrimaryButton
import com.example.wuwaweather.ui.TextLink
import com.example.wuwaweather.ui.components.GlassCard
import com.example.wuwaweather.ui.components.ReliabilityCard
import com.example.wuwaweather.ui.theme.Manrope
import com.example.wuwaweather.ui.theme.Velora
import kotlinx.coroutines.delay
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

    // Adhan file pickers (the permission to read the file is kept across restarts)
    fun persist(uri: Uri) = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val pickAdhan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); update { it.copy(adhanUri = uri.toString(), adhanEnabled = true) } }
    }
    val pickFajrAdhan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); update { it.copy(fajrAdhanUri = uri.toString()) } }
    }

    val h = home
    if (h == null) {
        FeatureScreen(tr("Prayer times", "নামাজের সময়"), null, bottomPadding) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(tr("Open the Weather tab once so Velora knows your location.", "লোকেশন জানতে একবার আবহাওয়া ট্যাব খুলুন।"), color = Velora.TextPrimary)
                }
            }
        }
        return
    }

    val zone = ZoneOffset.ofTotalSeconds(h.tzOffsetSeconds)
    val today = java.time.Instant.ofEpochMilli(now).atOffset(zone).toLocalDate()
    val day = remember(today, h, s.method, s.madhab) { PrayerCalculator.calculate(today, h.lat, h.lon, h.tzOffsetSeconds, s.method, s.madhab) }
    val tomorrow = remember(today, h, s.method, s.madhab) { PrayerCalculator.calculate(today.plusDays(1), h.lat, h.lon, h.tzOffsetSeconds, s.method, s.madhab) }
    val hijri = remember(today, s.hijriOffset, s.language) { runCatching { Hijri.of(today, s.hijriOffset) }.getOrNull() }
    val bangla = remember(today, s.language) { Bangabda.of(today) }

    val order = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
    val next = order.firstOrNull { day[it] > now }?.let { it to day[it] } ?: (Prayer.FAJR to tomorrow[Prayer.FAJR])
    val current = order.lastOrNull { day[it] <= now }

    FeatureScreen(
        title = tr("Prayer times", "নামাজের সময়"),
        subtitle = listOfNotNull(h.name, hijri?.toString(), if (s.isBangla) bangla.toString() else null).joinToString(" · "),
        bottomPadding = bottomPadding
    ) {
        item(key = "next") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(tr("NEXT PRAYER", "পরবর্তী নামাজ"), style = MaterialTheme.typography.labelMedium, color = Velora.TextTertiary)
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
                Text(tr("in ${countdown(next.second - now)}", "${countdown(next.second - now)} বাকি"), style = MaterialTheme.typography.bodyLarge, color = Velora.TextSecondary)
            }
        }

        item(key = "list") {
            GlassCard(Modifier.fillMaxWidth(), title = tr("Today", "আজ")) {
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
                            if (isCurrent) Text(tr("Now", "এখন"), style = MaterialTheme.typography.labelSmall, color = Velora.Accent)
                        }
                        Text(AlarmScheduler.formatTime(day[p], h.tzOffsetSeconds), style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary)
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

        // Exact-alarm permission (Android 12+)
        if (s.prayerAlerts.isNotEmpty() && !AlarmScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= 31) {
            item(key = "exact") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(tr("For on-time alerts, allow Velora to set alarms.", "সঠিক সময়ে অ্যালার্টের জন্য Velora-কে অ্যালার্ম সেট করার অনুমতি দিন।"),
                        color = Velora.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(tr("Without it, Android may deliver alerts a few minutes late.", "অনুমতি না দিলে অ্যালার্ট কয়েক মিনিট দেরিতে আসতে পারে।"),
                        color = Velora.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(tr("Allow alarms & reminders", "অ্যালার্ম ও রিমাইন্ডারের অনুমতি দিন"), {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    })
                }
            }
        }

        if (s.prayerAlerts.isNotEmpty() || s.suhurAlert || s.iftarAlert) {
            item(key = "reliable") { ReliabilityCard() }
        }

        // Adhan
        item(key = "adhan") {
            GlassCard(Modifier.fillMaxWidth(), title = tr("Adhan", "আজান")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Play Adhan at prayer alerts", "নামাজের অ্যালার্টে আজান বাজান"), style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary)
                        Text(tr("For prayers with the alert switch on", "যেসব নামাজের অ্যালার্ট চালু আছে"), style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
                    }
                    Switch(
                        checked = s.adhanEnabled && s.adhanUri != null,
                        onCheckedChange = { on ->
                            if (on && s.adhanUri == null) pickAdhan.launch(arrayOf("audio/*"))
                            else update { it.copy(adhanEnabled = on) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Velora.Accent, checkedThumbColor = Color(0xFF1A1A1A))
                    )
                }
                Spacer(Modifier.height(8.dp))
                AdhanFileRow(
                    label = tr("Adhan sound", "আজানের অডিও"),
                    uri = s.adhanUri,
                    context = context,
                    onPick = { pickAdhan.launch(arrayOf("audio/*")) },
                    onPreview = { s.adhanUri?.let { AdhanService.preview(context, it) } }
                )
                AdhanFileRow(
                    label = tr("Fajr Adhan (optional)", "ফজরের আজান (ঐচ্ছিক)"),
                    uri = s.fajrAdhanUri,
                    context = context,
                    onPick = { pickFajrAdhan.launch(arrayOf("audio/*")) },
                    onPreview = { s.fajrAdhanUri?.let { AdhanService.preview(context, it) } },
                    onClear = if (s.fajrAdhanUri != null) ({ update { it.copy(fajrAdhanUri = null) } }) else null
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Choose an Adhan audio file (MP3) saved on your phone. It plays at alarm volume, even in silent mode; tap Stop on the notification to end it.",
                        "ফোনে রাখা আজানের অডিও ফাইল (MP3) বেছে নিন। সাইলেন্ট মোডেও অ্যালার্মের ভলিউমে বাজবে; বন্ধ করতে নোটিফিকেশনের Stop চাপুন।"),
                    style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary
                )
            }
        }

        // Qibla compass
        item(key = "qibla") { QiblaCard(h.lat, h.lon) }

        // Calculation settings
        item(key = "settings") {
            GlassCard(Modifier.fillMaxWidth(), title = tr("Calculation", "হিসাব পদ্ধতি")) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrayerMethod.values().forEach { m -> Pill(m.label, selected = s.method == m) { update { it.copy(method = m) } } }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Madhab.values().forEach { m -> Pill(tr("Asr: ", "আসর: ") + m.label, selected = s.madhab == m) { update { it.copy(madhab = m) } } }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("Times are calculated on your phone for ${h.name} and work offline.", "${h.name}-এর সময় আপনার ফোনেই হিসাব হয়, ইন্টারনেট ছাড়াও কাজ করে।"),
                    style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun AdhanFileRow(
    label: String,
    uri: String?,
    context: Context,
    onPick: () -> Unit,
    onPreview: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    val name = remember(uri) { uri?.let { fileName(context, Uri.parse(it)) } }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary)
            Text(name ?: tr("Not chosen", "বেছে নেওয়া হয়নি"), style = MaterialTheme.typography.bodyMedium, color = Velora.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (uri != null) TextLink(tr("▶ Play", "▶ শুনুন"), onPreview)
        if (onClear != null) TextLink(tr("Clear", "মুছুন"), onClear)
        TextLink(tr("Choose", "বাছুন"), onPick)
    }
}

private fun fileName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

private fun countdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hh = total / 3600
    val mm = (total % 3600) / 60
    val ss = total % 60
    return if (hh > 0) tr("${hh}h ${mm}m", "${hh} ঘণ্টা ${mm} মিনিট") else tr("${mm}m ${ss}s", "${mm} মিনিট ${ss} সেকেন্ড")
}

@Suppress("unused")
private val keepFormatter = DateTimeFormatter.ISO_DATE
@Suppress("unused")
private val keepLocale = I18n.locale
