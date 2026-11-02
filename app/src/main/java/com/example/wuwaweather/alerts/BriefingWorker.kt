package com.example.wuwaweather.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.wuwaweather.BuildConfig
import com.example.wuwaweather.WeatherClient
import com.example.wuwaweather.calendar.Events
import com.example.wuwaweather.calendar.Hijri
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.NowcastAnalyzer
import com.example.wuwaweather.data.Place
import com.example.wuwaweather.data.UnitSystem
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.data.WeatherRepository
import com.example.wuwaweather.i18n.localDigits
import com.example.wuwaweather.i18n.tr
import com.example.wuwaweather.prayer.Prayer
import com.example.wuwaweather.prayer.PrayerCalculator
import java.time.Instant
import java.time.ZoneOffset

/**
 * Morning briefing: one notification with today's temperatures, rain, air quality,
 * prayer / fasting times and any Islamic date or holiday.
 */
class BriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val s = VeloraSettings.get(ctx)
        val home = HomeStore.get(ctx) ?: return Result.success()
        val units = runCatching {
            UnitSystem.valueOf(ctx.getSharedPreferences("velora_prefs", Context.MODE_PRIVATE).getString("units", "METRIC")!!)
        }.getOrDefault(UnitSystem.METRIC)

        val bundle = runCatching { WeatherRepository().load(Place.Coords(home.lat, home.lon), units) }.getOrNull()
        val zone = ZoneOffset.ofTotalSeconds(home.tzOffsetSeconds)
        val today = Instant.now().atOffset(zone).toLocalDate()
        val prayers = PrayerCalculator.calculate(today, home.lat, home.lon, home.tzOffsetSeconds, s.method, s.madhab)
        val hijri = runCatching { Hijri.of(today, s.hijriOffset) }.getOrNull()
        val lines = mutableListOf<String>()

        if (bundle != null) {
            val c = bundle.current
            val todayForecast = bundle.daily.firstOrNull()
            lines += "${c.description} · " + tr(
                "${todayForecast?.max ?: c.tempMax}° / ${todayForecast?.min ?: c.tempMin}°",
                "সর্বোচ্চ ${todayForecast?.max ?: c.tempMax}° / সর্বনিম্ন ${todayForecast?.min ?: c.tempMin}°"
            )
            val rainChance = todayForecast?.popPercent ?: 0
            if (rainChance >= 30) lines += tr("Rain chance $rainChance% today", "আজ বৃষ্টির সম্ভাবনা $rainChance%")
            bundle.air?.let { a ->
                if (a.index >= 4) lines += tr("Air quality is poor — consider a mask", "বায়ুর মান খারাপ — মাস্ক পরার কথা ভাবুন")
            }
            bundle.uv?.todayMax?.let { if (it >= 8) lines += tr("Very high UV around midday", "দুপুরে ইউভি অনেক বেশি") }
        }
        // Rain in the next couple of hours
        runCatching { WeatherClient.openMeteo.getExtras(home.lat, home.lon).body() }.getOrNull()?.let { res ->
            NowcastAnalyzer.nowcast(res, System.currentTimeMillis() / 1000, home.tzOffsetSeconds)?.let { nc ->
                if (nc.slots.any { it.precipMm >= 0.1 }) lines += nc.summary
            }
        }
        if (hijri?.isRamadan == true) {
            lines += tr(
                "Iftar ${AlarmScheduler.formatTime(prayers[Prayer.MAGHRIB], home.tzOffsetSeconds)}",
                "ইফতার ${AlarmScheduler.formatTime(prayers[Prayer.MAGHRIB], home.tzOffsetSeconds)}"
            )
        } else {
            lines += tr(
                "Dhuhr ${AlarmScheduler.formatTime(prayers[Prayer.DHUHR], home.tzOffsetSeconds)} · Maghrib ${AlarmScheduler.formatTime(prayers[Prayer.MAGHRIB], home.tzOffsetSeconds)}",
                "যোহর ${AlarmScheduler.formatTime(prayers[Prayer.DHUHR], home.tzOffsetSeconds)} · মাগরিব ${AlarmScheduler.formatTime(prayers[Prayer.MAGHRIB], home.tzOffsetSeconds)}"
            )
        }
        Events.on(today, s.hijriOffset).forEach { lines += "★ ${it.title}" }

        val title = tr("Good morning · ", "শুভ সকাল · ") + home.name + (hijri?.let { " · $it" } ?: "")
        Notifications.show(ctx, Notifications.CH_REMINDERS, 8200, title.localDigits(), lines.joinToString("\n").localDigits())
        return Result.success()
    }

    companion object {
        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<BriefingWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

@Suppress("unused")
private val keepKey = BuildConfig.OPENWEATHER_API_KEY.length
