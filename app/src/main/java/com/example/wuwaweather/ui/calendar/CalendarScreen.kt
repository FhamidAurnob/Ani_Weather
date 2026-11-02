package com.example.wuwaweather.ui.calendar

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.wuwaweather.i18n.Text
import com.example.wuwaweather.i18n.tr
import com.example.wuwaweather.i18n.I18n
import com.example.wuwaweather.calendar.Bangabda
import com.example.wuwaweather.calendar.CalendarEvent
import com.example.wuwaweather.calendar.Events
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wuwaweather.alerts.AlarmScheduler
import com.example.wuwaweather.alerts.Notifications
import com.example.wuwaweather.calendar.Hijri
import com.example.wuwaweather.calendar.HijriDate
import com.example.wuwaweather.calendar.Note
import com.example.wuwaweather.calendar.NotesStore
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.SettingsData
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.prayer.Prayer
import com.example.wuwaweather.prayer.PrayerCalculator
import com.example.wuwaweather.ui.FeatureScreen
import com.example.wuwaweather.ui.Pill
import com.example.wuwaweather.ui.components.GlassCard
import com.example.wuwaweather.ui.theme.Manrope
import com.example.wuwaweather.ui.theme.Velora
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val settingsFlow = remember { VeloraSettings.flow(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle()
    val s = settings ?: SettingsData()
    val notesFlow = remember { NotesStore.flow(context) }
    val notes by notesFlow.collectAsStateWithLifecycle()
    val home by HomeStore.home.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { HomeStore.get(context) }

    val today = LocalDate.now()
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }   // "2026-11"
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val ym = YearMonth.parse(month)
    val sel = LocalDate.parse(selected)

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun update(transform: (SettingsData) -> SettingsData) {
        VeloraSettings.update(context, transform)
        AlarmScheduler.rescheduleAll(context)
    }

    val todayHijri = remember(today, s.hijriOffset, s.language) { runCatching { Hijri.of(today, s.hijriOffset) }.getOrNull() }
    val todayBangla = remember(today, s.language) { Bangabda.of(today) }

    FeatureScreen(
        title = tr("Calendar", "ক্যালেন্ডার"),
        subtitle = listOfNotNull(
            today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", I18n.locale)),
            todayHijri?.toString(),
            todayBangla.toString()
        ).joinToString(" · "),
        bottomPadding = bottomPadding
    ) {
        // Ramadan countdown / progress
        item(key = "ramadan") { RamadanCard(today, s.hijriOffset, todayHijri) }

        // Islamic dates & holidays
        item(key = "upcoming") {
            val upcoming = remember(today, s.hijriOffset, s.language) { Events.upcoming(today, s.hijriOffset, 200).take(5) }
            GlassCard(Modifier.fillMaxWidth(), title = tr("Upcoming dates", "আসন্ন দিনগুলো")) {
                upcoming.forEach { e -> EventRow(e, today) { selected = e.date.toString(); month = YearMonth.from(e.date).toString() } }
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    tr("Remind me on the day", "সেদিন মনে করিয়ে দিন"),
                    tr("Islamic dates and holidays, at 9 AM", "ইসলামি দিন ও ছুটি, সকাল ৯টায়"),
                    s.eventReminders
                ) { on -> if (on) ensureNotifications(); update { it.copy(eventReminders = on) } }
                Text(
                    tr("Lunar dates are estimates until the moon is sighted.", "চাঁদ দেখার আগ পর্যন্ত চান্দ্র তারিখগুলো আনুমানিক।"),
                    style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary
                )
            }
        }

        // Suhur & Iftar
        item(key = "fasting") {
            GlassCard(Modifier.fillMaxWidth(), title = tr("Suhur & Iftar", "সাহরি ও ইফতার")) {
                val h = home
                if (h == null) {
                    Text(tr("Open the Weather tab once so Velora knows your location.", "লোকেশন জানতে একবার আবহাওয়া ট্যাব খুলুন।"), color = Velora.TextSecondary)
                } else {
                    val day = remember(sel, h, s.method, s.madhab) {
                        PrayerCalculator.calculate(sel, h.lat, h.lon, h.tzOffsetSeconds, s.method, s.madhab)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        TimeBlock(tr("Sehri ends", "সাহরির শেষ সময়"), AlarmScheduler.formatTime(day[Prayer.FAJR], h.tzOffsetSeconds), Modifier.weight(1f))
                        TimeBlock(tr("Iftar", "ইফতার"), AlarmScheduler.formatTime(day[Prayer.MAGHRIB], h.tzOffsetSeconds), Modifier.weight(1f))
                    }
                    Text(
                        "${sel.format(DateTimeFormatter.ofPattern("EEE d MMM", I18n.locale))} · ${h.name}",
                        style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = Velora.Divider, thickness = 0.5.dp)
                    SwitchRow(tr("Suhur reminder", "সাহরির রিমাইন্ডার"), tr("${s.suhurMinutesBefore} min before Sehri ends", "সাহরি শেষের ${s.suhurMinutesBefore} মিনিট আগে"), s.suhurAlert) { on ->
                        if (on) ensureNotifications(); update { it.copy(suhurAlert = on) }
                    }
                    if (s.suhurAlert) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            listOf(15, 30, 45, 60).forEach { m ->
                                Pill(tr("$m min", "$m মিনিট"), selected = s.suhurMinutesBefore == m) { update { it.copy(suhurMinutesBefore = m) } }
                            }
                        }
                    }
                    SwitchRow(tr("Iftar reminder", "ইফতারের রিমাইন্ডার"), tr("At Maghrib", "মাগরিবের সময়"), s.iftarAlert) { on ->
                        if (on) ensureNotifications(); update { it.copy(iftarAlert = on) }
                    }
                    SwitchRow(tr("Only during Ramadan", "শুধু রমজানে"), tr("Turn off for Sunnah / voluntary fasts", "সুন্নত / নফল রোজার জন্য বন্ধ করুন"), s.fastingAlertsOnlyRamadan) { on ->
                        update { it.copy(fastingAlertsOnlyRamadan = on) }
                    }
                }
            }
        }

        if (s.suhurAlert || s.iftarAlert) {
            item(key = "reliable") { com.example.wuwaweather.ui.components.ReliabilityCard() }
        }

        // Month grid
        item(key = "month") {
            MonthCard(
                ym = ym,
                today = today,
                selected = sel,
                hijriOffset = s.hijriOffset,
                noteDates = notes.orEmpty().map { it.date }.toSet(),
                language = s.language,
                onPrev = { month = ym.minusMonths(1).toString() },
                onNext = { month = ym.plusMonths(1).toString() },
                onSelect = { selected = it.toString() }
            )
        }

        // Selected day: notes & to-dos
        item(key = "day") {
            DayNotesCard(
                date = sel,
                hijri = runCatching { Hijri.of(sel, s.hijriOffset) }.getOrNull(),
                events = remember(sel, s.hijriOffset, s.language) { Events.on(sel, s.hijriOffset) },
                notes = notes.orEmpty().filter { it.date == selected }.sortedBy { it.id },
                onAdd = { note ->
                    if (note.remindAt != null) ensureNotifications()
                    NotesStore.upsert(context, note)
                    AlarmScheduler.rescheduleAll(context)
                },
                onToggleDone = { note ->
                    NotesStore.upsert(context, note.copy(done = !note.done))
                    AlarmScheduler.rescheduleAll(context)
                },
                onDelete = { note ->
                    AlarmScheduler.cancelNote(context, note.id)
                    NotesStore.delete(context, note.id)
                }
            )
        }

        // Hijri adjustment
        item(key = "hijri") {
            GlassCard(Modifier.fillMaxWidth(), title = tr("Hijri date adjustment", "হিজরি তারিখ সমন্বয়")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (-2..2).forEach { d ->
                        Pill(if (d > 0) "+$d" else "$d", selected = s.hijriOffset == d) { update { it.copy(hijriOffset = d) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Based on the Umm al-Qura calendar. Bangladesh usually follows local moon sighting, about 1 day behind Saudi Arabia (−1). Adjust if your local date differs.",
                        "উম্মুল কুরা ক্যালেন্ডারের ভিত্তিতে। বাংলাদেশে সাধারণত স্থানীয় চাঁদ দেখা অনুসারে সৌদি আরবের প্রায় ১ দিন পরে (−১)। স্থানীয় তারিখ ভিন্ন হলে সমন্বয় করুন।"),
                    style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun RamadanCard(today: LocalDate, offset: Int, todayHijri: HijriDate?) {
    GlassCard(Modifier.fillMaxWidth()) {
        if (todayHijri?.isRamadan == true) {
            Text(tr("RAMADAN MUBARAK", "রমজান মোবারক"), style = MaterialTheme.typography.labelMedium, color = Velora.Accent)
            Spacer(Modifier.height(4.dp))
            Text(tr("Day ${todayHijri.day} of Ramadan ${todayHijri.year}", "রমজান ${todayHijri.year}-এর ${todayHijri.day}তম দিন"), fontFamily = Manrope, fontWeight = FontWeight.ExtraLight, fontSize = 34.sp, color = Velora.TextPrimary)
            Spacer(Modifier.height(10.dp))
            ProgressBar(todayHijri.day / 30f)
        } else {
            val start = remember(today, offset) { Hijri.nextRamadanStart(today, offset) }
            Text(tr("RAMADAN", "রমজান"), style = MaterialTheme.typography.labelMedium, color = Velora.Accent)
            Spacer(Modifier.height(4.dp))
            if (start != null) {
                val days = ChronoUnit.DAYS.between(today, start)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$days", fontFamily = Manrope, fontWeight = FontWeight.ExtraLight, fontSize = 52.sp, color = Velora.TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (days == 1L) tr("day to go", "দিন বাকি") else tr("days to go", "দিন বাকি"), style = MaterialTheme.typography.titleMedium, color = Velora.TextSecondary, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(
                    tr("Expected to begin ${start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", I18n.locale))} (subject to moon sighting)",
                        "সম্ভাব্য শুরু ${start.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", I18n.locale))} (চাঁদ দেখা সাপেক্ষে)"),
                    style = MaterialTheme.typography.bodyMedium, color = Velora.TextSecondary
                )
            } else {
                Text(tr("Couldn't calculate the next Ramadan date.", "পরবর্তী রমজানের তারিখ হিসাব করা যায়নি।"), color = Velora.TextSecondary)
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x26FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(Velora.Accent))
    }
}

@Composable
private fun TimeBlock(label: String, time: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary)
        Text(time, fontFamily = Manrope, fontWeight = FontWeight.Light, fontSize = 30.sp, color = Velora.TextPrimary)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Velora.Accent, checkedThumbColor = Color(0xFF1A1A1A))
        )
    }
}

@Composable
private fun MonthCard(
    ym: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    hijriOffset: Int,
    noteDates: Set<String>,
    language: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val firstDow: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val first = ym.atDay(1)
    val lead = ((first.dayOfWeek.value - firstDow.value) + 7) % 7
    val gridStart = first.minusDays(lead.toLong())
    val cells = remember(ym, hijriOffset, firstDow, language) {
        (0 until 42).map { i ->
            val d = gridStart.plusDays(i.toLong())
            d to runCatching { Hijri.of(d, hijriOffset) }.getOrNull()
        }
    }
    val events = remember(ym, hijriOffset, language) { cells.associate { (d, _) -> d to Events.on(d, hijriOffset) } }
    val hFirst = cells.firstOrNull { it.first == first }?.second
    val hLast = cells.firstOrNull { it.first == ym.atEndOfMonth() }?.second

    GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.KeyboardArrowLeft, tr("Previous month", "আগের মাস"), tint = Velora.TextPrimary) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", I18n.locale)), style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary)
                if (hFirst != null && hLast != null) {
                    Text(
                        if (hFirst.month == hLast.month) "${hFirst.monthName} ${hFirst.year}"
                        else "${hFirst.monthName} – ${hLast.monthName} ${hLast.year}",
                        style = MaterialTheme.typography.labelSmall, color = Velora.Accent
                    )
                }
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.KeyboardArrowRight, tr("Next month", "পরের মাস"), tint = Velora.TextPrimary) }
        }
        Spacer(Modifier.height(6.dp))
        // Weekday labels
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                val dow = firstDow.plus(i.toLong())
                Text(
                    dow.getDisplayName(TextStyle.NARROW, I18n.locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dow == DayOfWeek.FRIDAY) Velora.Accent else Velora.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { (d, h) ->
                    DayCell(
                        date = d,
                        hijri = h,
                        inMonth = d.month == ym.month,
                        isToday = d == today,
                        isSelected = d == selected,
                        hasNotes = d.toString() in noteDates,
                        dayEvents = events[d].orEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(d) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    hijri: HijriDate?,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasNotes: Boolean,
    dayEvents: List<CalendarEvent>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val holiday = dayEvents.any { it.isHoliday }
    val shape = RoundedCornerShape(14.dp)
    val ramadan = hijri?.isRamadan == true
    Box(
        modifier
            .aspectRatio(0.82f)
            .padding(2.dp)
            .clip(shape)
            .background(
                when {
                    isSelected -> Velora.Accent
                    ramadan && inMonth -> Color(0x1F6BD68A)
                    else -> Color.Transparent
                }
            )
            .then(if (isToday && !isSelected) Modifier.border(1.dp, Velora.Accent, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val base = when {
            isSelected -> Color(0xFF1A1A1A)
            holiday -> Color(0xFFFF8A80)    // public holidays in soft red
            else -> Velora.TextPrimary
        }
        val alpha = if (inMonth) 1f else 0.35f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = base.copy(alpha = alpha)
            )
            Text(
                hijri?.day?.toString() ?: "",
                fontSize = 9.sp,
                color = (if (isSelected) Color(0xFF1A1A1A) else Velora.Accent).copy(alpha = 0.8f * alpha)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    Modifier.size(4.dp).clip(CircleShape)
                        .background(if (hasNotes) (if (isSelected) Color(0xFF1A1A1A) else Velora.Rain) else Color.Transparent)
                )
                if (dayEvents.isNotEmpty()) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSelected) Color(0xFF1A1A1A) else Velora.Accent))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayNotesCard(
    date: LocalDate,
    hijri: HijriDate?,
    events: List<CalendarEvent>,
    notes: List<Note>,
    onAdd: (Note) -> Unit,
    onToggleDone: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    var text by rememberSaveable(date.toString()) { mutableStateOf("") }
    var isTodo by rememberSaveable(date.toString()) { mutableStateOf(true) }
    var remindMinutes by rememberSaveable(date.toString()) { mutableStateOf<Int?>(null) }   // minutes after midnight
    var showPicker by remember { mutableStateOf(false) }

    GlassCard(
        Modifier.fillMaxWidth(),
        title = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", I18n.locale)) + (hijri?.let { " · $it" } ?: "") + " · " + Bangabda.of(date)
    ) {
        events.forEach { e ->
            Text(
                (if (e.isHoliday) "● " else "◦ ") + e.title + if (e.isHoliday) tr(" · holiday", " · ছুটি") else "",
                style = MaterialTheme.typography.bodyLarge,
                color = if (e.isHoliday) Color(0xFFFF8A80) else Velora.Accent
            )
            Spacer(Modifier.height(4.dp))
        }
        if (notes.isEmpty() && events.isEmpty()) {
            Text(tr("Nothing planned. Add a note or to-do below.", "কিছু পরিকল্পনা নেই। নিচে নোট বা করণীয় যোগ করুন।"), style = MaterialTheme.typography.bodyMedium, color = Velora.TextTertiary)
        }
        notes.forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (n.isTodo) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (n.done) Velora.Accent else Color.Transparent)
                            .border(1.5.dp, if (n.done) Velora.Accent else Velora.TextSecondary, CircleShape)
                            .clickable { onToggleDone(n) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (n.done) Icon(Icons.Rounded.Check, null, tint = Color(0xFF1A1A1A), modifier = Modifier.size(14.dp))
                    }
                } else {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Velora.Rain))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        n.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (n.done) Velora.TextTertiary else Velora.TextPrimary,
                        textDecoration = if (n.done) TextDecoration.LineThrough else null
                    )
                    n.remindAt?.let {
                        Text(
                            "⏰ " + DateTimeFormatter.ofPattern("h:mm a", I18n.locale).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary
                        )
                    }
                }
                IconButton(onClick = { onDelete(n) }) {
                    Icon(Icons.Rounded.Close, tr("Delete", "মুছুন"), tint = Velora.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        // Composer
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Velora.GlassFill)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text(if (isTodo) tr("Add a to-do…", "করণীয় যোগ করুন…") else tr("Add a note…", "নোট যোগ করুন…"), color = Velora.TextTertiary, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Velora.TextPrimary),
                    cursorBrush = SolidColor(Velora.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank()) {
                            onAdd(buildNote(date, text, isTodo, remindMinutes))
                            text = ""; remindMinutes = null
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(tr("To-do", "করণীয়"), selected = isTodo) { isTodo = true }
            Pill(tr("Note", "নোট"), selected = !isTodo) { isTodo = false }
            Pill(remindMinutes?.let { "⏰ " + LocalTime.of(it / 60, it % 60).format(DateTimeFormatter.ofPattern("h:mm a", I18n.locale)) } ?: tr("⏰ Remind", "⏰ রিমাইন্ডার"),
                selected = remindMinutes != null) { showPicker = true }
            Spacer(Modifier.weight(1f))
            Pill(tr("Add", "যোগ করুন"), selected = text.isNotBlank()) {
                if (text.isNotBlank()) {
                    onAdd(buildNote(date, text, isTodo, remindMinutes))
                    text = ""; remindMinutes = null
                }
            }
        }
    }

    if (showPicker) {
        val now = LocalTime.now()
        val picker = rememberTimePickerState(initialHour = remindMinutes?.div(60) ?: now.hour, initialMinute = remindMinutes?.rem(60) ?: 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { remindMinutes = picker.hour * 60 + picker.minute; showPicker = false }) { Text(tr("Set", "ঠিক আছে")) } },
            dismissButton = {
                TextButton(onClick = { remindMinutes = null; showPicker = false }) { Text(tr("No reminder", "রিমাইন্ডার নেই")) }
            },
            text = { TimePicker(state = picker) },
            containerColor = Color(0xFF151B38)
        )
    }
}

private fun buildNote(date: LocalDate, text: String, isTodo: Boolean, remindMinutes: Int?): Note {
    val remindAt = remindMinutes?.let {
        date.atTime(it / 60, it % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return Note(id = System.currentTimeMillis(), date = date.toString(), text = text.trim(), isTodo = isTodo, remindAt = remindAt)
}


@Composable
private fun EventRow(e: CalendarEvent, today: LocalDate, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(today, e.date)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (e.isHoliday) Color(0xFFFF8A80) else Velora.Accent))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary)
            Text(e.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", I18n.locale)), style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
        }
        Text(
            when (days) {
                0L -> tr("Today", "আজ")
                1L -> tr("Tomorrow", "আগামীকাল")
                else -> tr("in $days days", "$days দিন পর")
            },
            style = MaterialTheme.typography.labelLarge, color = Velora.TextSecondary
        )
    }
}
