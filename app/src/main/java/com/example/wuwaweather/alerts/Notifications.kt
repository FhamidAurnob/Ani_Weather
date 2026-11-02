package com.example.wuwaweather.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.wuwaweather.MainActivity
import com.example.wuwaweather.R

object Notifications {
    const val CH_PRAYER = "prayer"
    const val CH_FASTING = "fasting"
    const val CH_REMINDERS = "reminders"
    const val CH_QUAKES = "quakes"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        fun ch(id: String, name: String, desc: String) = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = desc
            setSound(alarmSound, audio)
            enableVibration(true)
        }
        nm.createNotificationChannels(
            listOf(
                ch(CH_PRAYER, "Prayer times", "Alerts when it's time to pray"),
                ch(CH_FASTING, "Suhur & Iftar", "Suhur and Iftar reminders"),
                ch(CH_REMINDERS, "Calendar reminders", "Reminders for your notes and to-dos"),
                ch(CH_QUAKES, "Earthquake alerts", "Strong earthquakes near you"),
            )
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (channel == CH_QUAKES) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) { }
    }
}
