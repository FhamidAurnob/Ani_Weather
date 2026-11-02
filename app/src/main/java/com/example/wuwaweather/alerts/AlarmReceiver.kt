package com.example.wuwaweather.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wuwaweather.quakes.QuakeWorker

/** Fires a scheduled alert, then schedules the next ones. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.show(
            context,
            channel = intent.getStringExtra(AlarmScheduler.EXTRA_CHANNEL) ?: Notifications.CH_REMINDERS,
            id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0),
            title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Velora",
            text = intent.getStringExtra(AlarmScheduler.EXTRA_TEXT) ?: ""
        )
        AlarmScheduler.rescheduleAll(context)
    }
}

/** Restores alarms after reboot, app update, or clock/time-zone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
        QuakeWorker.schedule(context)
    }
}
