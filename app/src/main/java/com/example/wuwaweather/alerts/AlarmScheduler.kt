package com.example.wuwaweather.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.wuwaweather.calendar.Hijri
import com.example.wuwaweather.calendar.NotesStore
import com.example.wuwaweather.data.Home
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.SettingsData
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.prayer.Prayer
import com.example.wuwaweather.prayer.PrayerCalculator
import com.example.wuwaweather.prayer.PrayerDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Schedules every upcoming alert: the next occurrence of each enabled prayer, Suhur, Iftar,
 * and future note reminders. Called on app start, settings changes, after each alarm and after reboot.
 */
object AlarmScheduler {

    const val EXTRA_CHANNEL = "channel"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_ID = "id"

    private const val RC_PRAYER = 100      // + Prayer.ordinal
    private const val RC_SUHUR = 200
    private const val RC_IFTAR = 201
    private const val RC_NOTE = 10_000     // + note id hash

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun rescheduleAll(context: Context) {
        val s = VeloraSettings.get(context)
        val home = HomeStore.get(context)
        val now = System.currentTimeMillis()

        // Prayers
        for (p in Prayer.values()) {
            val rc = RC_PRAYER + p.ordinal
            if (home == null || p !in s.prayerAlerts) { cancel(context, rc); continue }
            val at = nextOccurrence(home, s, now) { it[p] }
            if (at == null) cancel(context, rc)
            else schedule(
                context, rc, at, Notifications.CH_PRAYER,
                if (p == Prayer.SUNRISE) "Sunrise" else "It's time for ${p.label}",
                "${p.label} · ${formatTime(at, home.tzOffsetSeconds)} in ${home.name}"
            )
        }

        // Suhur & Iftar
        if (home != null && s.suhurAlert) {
            val at = nextOccurrence(home, s, now, fastingDay = true) { it[Prayer.FAJR] - s.suhurMinutesBefore * 60_000L }
            if (at != null) {
                val fajr = at + s.suhurMinutesBefore * 60_000L
                schedule(context, RC_SUHUR, at, Notifications.CH_FASTING, "Suhur ends in ${s.suhurMinutesBefore} min",
                    "Sehri ends at ${formatTime(fajr, home.tzOffsetSeconds)} (Fajr). Finish your Suhur.")
            } else cancel(context, RC_SUHUR)
        } else cancel(context, RC_SUHUR)

        if (home != null && s.iftarAlert) {
            val at = nextOccurrence(home, s, now, fastingDay = true) { it[Prayer.MAGHRIB] }
            if (at != null) {
                schedule(context, RC_IFTAR, at, Notifications.CH_FASTING, "It's time for Iftar",
                    "Maghrib · ${formatTime(at, home.tzOffsetSeconds)}. May Allah accept your fast.")
            } else cancel(context, RC_IFTAR)
        } else cancel(context, RC_IFTAR)

        // Notes & to-dos
        for (note in NotesStore.all(context)) {
            val rc = noteRequestCode(note.id)
            val at = note.remindAt
            if (at != null && at > now && !note.done) {
                schedule(context, rc, at, Notifications.CH_REMINDERS, if (note.isTodo) "To-do" else "Reminder", note.text)
            } else cancel(context, rc)
        }
    }

    fun cancelNote(context: Context, id: Long) = cancel(context, noteRequestCode(id))

    /** Finds the first time >= now produced by [pick], looking ahead day by day. */
    private fun nextOccurrence(
        home: Home,
        s: SettingsData,
        now: Long,
        fastingDay: Boolean = false,
        pick: (PrayerDay) -> Long
    ): Long? {
        var date = LocalDate.now(ZoneOffset.ofTotalSeconds(home.tzOffsetSeconds))
        repeat(400) {
            val skip = fastingDay && s.fastingAlertsOnlyRamadan &&
                runCatching { !Hijri.of(date, s.hijriOffset).isRamadan }.getOrDefault(true)
            if (!skip) {
                val day = PrayerCalculator.calculate(date, home.lat, home.lon, home.tzOffsetSeconds, s.method, s.madhab)
                val t = pick(day)
                if (t > now + 5_000) return t
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun schedule(context: Context, rc: Int, at: Long, channel: String, title: String, text: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_CHANNEL, channel)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_ID, rc)
        val pi = PendingIntent.getBroadcast(context, rc, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(context: Context, rc: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, rc, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun noteRequestCode(id: Long) = RC_NOTE + (id % 1_000_000L).toInt()

    fun formatTime(epochMillis: Long, tzOffsetSeconds: Int): String =
        DateTimeFormatter.ofPattern("h:mm a").format(
            Instant.ofEpochMilli(epochMillis + 30_000).atOffset(ZoneOffset.ofTotalSeconds(tzOffsetSeconds))
        )
}
