package com.example.wuwaweather.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.prayer.Prayer
import com.example.wuwaweather.quakes.QuakeWorker

/** Fires a scheduled alert (playing the Adhan for prayers if enabled), then schedules the next ones. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = VeloraSettings.get(context)   // also sets the app language
        val channel = intent.getStringExtra(AlarmScheduler.EXTRA_CHANNEL) ?: Notifications.CH_REMINDERS
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0)
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Velora"
        val text = intent.getStringExtra(AlarmScheduler.EXTRA_TEXT) ?: ""
        val prayer = intent.getStringExtra(AlarmScheduler.EXTRA_PRAYER)?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }

        val adhanUri = when {
            prayer == null || prayer == Prayer.SUNRISE || !settings.adhanEnabled -> null
            prayer == Prayer.FAJR -> settings.fajrAdhanUri ?: settings.adhanUri
            else -> settings.adhanUri
        }
        val played = adhanUri != null && AdhanService.start(context, adhanUri, title, text)
        if (!played) Notifications.show(context, channel, id, title, text)

        AlarmScheduler.rescheduleAll(context)
    }
}

/** Restores alarms after reboot, app update, or clock/time-zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        VeloraSettings.get(context)
        AlarmScheduler.rescheduleAll(context)
        QuakeWorker.schedule(context)
        WeatherAlertWorker.schedule(context)
    }
}
