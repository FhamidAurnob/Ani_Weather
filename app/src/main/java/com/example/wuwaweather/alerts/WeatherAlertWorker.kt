package com.example.wuwaweather.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.wuwaweather.BuildConfig
import com.example.wuwaweather.WeatherClient
import com.example.wuwaweather.storms.CycloneRepository
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.NowcastAnalyzer
import com.example.wuwaweather.data.SevereSignal
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.data.WeatherRepository
import com.example.wuwaweather.i18n.localDigits
import com.example.wuwaweather.i18n.tr
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Every 30 minutes: "rain starting soon" and severe-weather alerts for home. */
class WeatherAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val s = VeloraSettings.get(ctx)
        if (!s.rainAlerts && !s.severeAlerts && !s.healthAlerts && !s.cycloneAlerts) return Result.success()
        val home = HomeStore.get(ctx) ?: return Result.success()

        val res = runCatching { WeatherClient.openMeteo.getExtras(home.lat, home.lon).body() }.getOrNull()
            ?: return Result.retry()
        val now = System.currentTimeMillis() / 1000
        val prefs = ctx.getSharedPreferences("velora_weather_alerts", Context.MODE_PRIVATE)
        fun once(key: String, block: () -> Unit) {
            if (!prefs.getBoolean(key, false)) {
                block()
                prefs.edit().putBoolean(key, true).apply()
            }
        }
        fun hhmm(t: Long) = WeatherRepository.format(t, home.tzOffsetSeconds, "h:mm a")

        // Rain starting within the next hour
        if (s.rainAlerts) {
            val nc = NowcastAnalyzer.nowcast(res, now, home.tzOffsetSeconds)
            val at = nc?.rainSoonAt
            if (at != null && at - now <= 60 * 60) {
                once("rain_${at / 3600}") {
                    Notifications.show(
                        ctx, Notifications.CH_WEATHER, 8001,
                        tr("Rain starting soon", "শীঘ্রই বৃষ্টি শুরু হবে"),
                        tr("Rain likely around ${hhmm(at)} in ${home.name}. Take an umbrella.",
                            "${home.name}-এ ${hhmm(at)} নাগাদ বৃষ্টির সম্ভাবনা। ছাতা সঙ্গে রাখুন।")
                    )
                }
            }
        }

        // Severe weather in the next 12 hours (one alert per kind per 6-hour window)
        if (s.severeAlerts) {
            NowcastAnalyzer.severe(res, now).forEach { sig ->
                once("severe_${sig.kind}_${sig.atEpochSeconds / (6 * 3600)}") {
                    val (title, text) = when (sig.kind) {
                        SevereSignal.Kind.HEAVY_RAIN -> tr("Heavy rain expected", "ভারী বৃষ্টির সম্ভাবনা") to
                            tr("Around ${hhmm(sig.atEpochSeconds)} · up to ${sig.value.roundToInt()} mm/h in ${home.name}.",
                                "${hhmm(sig.atEpochSeconds)} নাগাদ · ঘণ্টায় ${sig.value.roundToInt()} মিমি পর্যন্ত · ${home.name}।".localDigits())
                        SevereSignal.Kind.THUNDER -> tr("Thunderstorm expected", "বজ্রঝড়ের সম্ভাবনা") to
                            tr("Thunderstorms likely around ${hhmm(sig.atEpochSeconds)} in ${home.name}. Stay indoors if you can.",
                                "${home.name}-এ ${hhmm(sig.atEpochSeconds)} নাগাদ বজ্রঝড়ের সম্ভাবনা। সম্ভব হলে ঘরে থাকুন।")
                        SevereSignal.Kind.STRONG_WIND -> tr("Strong wind expected", "দমকা হাওয়ার সম্ভাবনা") to
                            tr("Gusts up to ${sig.value.roundToInt()} km/h around ${hhmm(sig.atEpochSeconds)} in ${home.name}.",
                                "${home.name}-এ ${hhmm(sig.atEpochSeconds)} নাগাদ ঘণ্টায় ${sig.value.roundToInt()} কিমি পর্যন্ত দমকা হাওয়া।".localDigits())
                    }
                    Notifications.show(ctx, Notifications.CH_WEATHER, 8100 + sig.kind.ordinal, title, text)
                }
            }
        }
        val localHour = java.time.Instant.ofEpochSecond(now).atOffset(java.time.ZoneOffset.ofTotalSeconds(home.tzOffsetSeconds)).hour
        val dayKey = (now + home.tzOffsetSeconds) / 86_400

        // Dangerous heat (feels-like) in the next 12 hours — once per day, during waking hours
        if (s.healthAlerts && localHour in 7..20) {
            val h = res.hourly
            val times = h?.time.orEmpty()
            val peak = times.indices
                .filter { times[it] in now..(now + 12 * 3600) }
                .mapNotNull { i -> h?.apparentTemperature?.getOrNull(i)?.let { times[i] to it } }
                .maxByOrNull { it.second }
            if (peak != null && peak.second >= 38.0) {
                once("heat_$dayKey") {
                    val severe = peak.second >= 41.0
                    Notifications.show(
                        ctx, Notifications.CH_WEATHER, 8300,
                        if (severe) tr("Dangerous heat today", "আজ বিপজ্জনক গরম") else tr("Very hot today", "আজ অনেক গরম"),
                        tr("Feels like ${peak.second.roundToInt()}° around ${hhmm(peak.first)}. Drink water often and avoid the midday sun.",
                            "${hhmm(peak.first)} নাগাদ অনুভূত তাপমাত্রা ${peak.second.roundToInt()}°। বারবার পানি পান করুন, দুপুরের রোদ এড়িয়ে চলুন।").localDigits()
                    )
                }
            }
        }

        // Poor air quality — once per day, during waking hours
        if (s.healthAlerts && localHour in 7..20) {
            val aqi = runCatching {
                WeatherClient.api.getAirPollution(home.lat, home.lon, BuildConfig.OPENWEATHER_API_KEY).body()?.list?.firstOrNull()
            }.getOrNull()
            val index = aqi?.main?.aqi ?: 0
            if (index >= 4) {
                once("air_$dayKey") {
                    Notifications.show(
                        ctx, Notifications.CH_WEATHER, 8301,
                        if (index >= 5) tr("Very poor air quality", "বায়ুর মান অত্যন্ত খারাপ") else tr("Poor air quality", "বায়ুর মান খারাপ"),
                        (tr("In ${home.name}", "${home.name}-এ") + (aqi?.components?.pm25?.let { " · PM2.5 ${it.roundToInt()} µg/m³" } ?: "") +
                            tr(". Wear a mask outside and keep windows closed.", "। বাইরে মাস্ক পরুন, জানালা বন্ধ রাখুন।")).localDigits()
                    )
                }
            }
        }

        // Tropical cyclones nearby (GDACS)
        if (s.cycloneAlerts) {
            val cyclones = runCatching { CycloneRepository.active(home.lat, home.lon) }.getOrNull().orEmpty()
            cyclones.filter { c ->
                val d = c.distanceKm ?: return@filter false
                d <= 800 || (d <= 1500 && c.alertLevel.lowercase() in setOf("orange", "red"))
            }.forEach { c ->
                once("cyclone_${c.id}_${c.alertLevel}") {
                    Notifications.show(
                        ctx, Notifications.CH_WEATHER, 8400 + (c.id % 50).toInt(),
                        tr("Cyclone ${c.name} nearby", "ঘূর্ণিঝড় ${c.name} কাছাকাছি"),
                        (tr("${c.alertLevel} alert · ${c.distanceKm!!.roundToInt()} km from ${home.name}", "${c.alertLevel} সতর্কতা · ${home.name} থেকে ${c.distanceKm.roundToInt()} কিমি") +
                            (c.severity?.let { " · $it" } ?: "") +
                            tr(". Follow official BMD signals.", "। আবহাওয়া অধিদপ্তরের সংকেত অনুসরণ করুন।")).localDigits()
                    )
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "velora_weather_alerts"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WeatherAlertWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
