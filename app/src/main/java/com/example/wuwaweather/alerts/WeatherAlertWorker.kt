package com.example.wuwaweather.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.wuwaweather.WeatherClient
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
        if (!s.rainAlerts && !s.severeAlerts) return Result.success()
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
