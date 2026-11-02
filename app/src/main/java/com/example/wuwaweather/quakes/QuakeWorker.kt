package com.example.wuwaweather.quakes

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.wuwaweather.alerts.Notifications
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.i18n.localDigits
import com.example.wuwaweather.i18n.tr
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Every 15 minutes: alert about new strong earthquakes near home. */
class QuakeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val s = VeloraSettings.get(ctx)
        if (!s.quakeAlerts) return Result.success()
        val home = HomeStore.get(ctx) ?: return Result.success()

        val quakes = runCatching { QuakeRepository.load(QuakeFeed.DAY, home.lat, home.lon) }
            .getOrElse { return Result.retry() }

        val prefs = ctx.getSharedPreferences("velora_quakes", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("notified", emptySet())!!.toMutableSet()
        val cutoff = System.currentTimeMillis() - 6 * 3_600_000L   // ignore old events

        quakes.filter {
            it.magnitude >= s.quakeMinMagnitude &&
                (it.distanceKm ?: Double.MAX_VALUE) <= s.quakeRadiusKm &&
                it.time >= cutoff &&
                it.id !in seen
        }.forEach { q ->
            Notifications.show(
                ctx, Notifications.CH_QUAKES, q.id.hashCode(),
                tr("M${String.format(Locale.US, "%.1f", q.magnitude)} earthquake ${q.distanceKm!!.roundToInt()} km away",
                    "M${String.format(Locale.US, "%.1f", q.magnitude)} ভূমিকম্প, ${q.distanceKm.roundToInt()} কিমি দূরে").localDigits(),
                (tr("${q.place}. Depth ${q.depthKm.roundToInt()} km.", "${q.place}। গভীরতা ${q.depthKm.roundToInt()} কিমি।") +
                    if (q.tsunami) tr(" Tsunami alert issued.", " সুনামি সতর্কতা জারি।") else "").localDigits()
            )
            seen += q.id
        }
        prefs.edit().putStringSet("notified", seen.toList().takeLast(200).toSet()).apply()
        return Result.success()
    }

    companion object {
        private const val NAME = "velora_quake_check"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<QuakeWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
