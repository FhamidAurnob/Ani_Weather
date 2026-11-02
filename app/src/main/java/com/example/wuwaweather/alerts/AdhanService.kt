package com.example.wuwaweather.alerts

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.wuwaweather.MainActivity
import com.example.wuwaweather.R
import com.example.wuwaweather.i18n.tr

/** Plays the chosen Adhan once, with a "Stop" button. Swiping the notification away also stops it. */
class AdhanService : Service() {

    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val uri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        if (uri == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Adhan"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""

        Notifications.ensureChannels(this)
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        player?.release()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AdhanService, uri)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ -> stopSelf(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            stopSelf()
            null
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Notifications.CH_ADHAN)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setDeleteIntent(stop)
            .addAction(0, tr("Stop Adhan", "আজান বন্ধ করুন"), stop)
            .setOngoing(false)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.fhamid.velora.STOP_ADHAN"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val NOTIFICATION_ID = 7001

        /** Returns false if the service couldn't start (the caller then shows a normal notification). */
        fun start(context: Context, uri: String, title: String, text: String): Boolean = try {
            val i = Intent(context, AdhanService::class.java)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(context, i)
            true
        } catch (e: Exception) {
            false
        }

        /** Plays a quick preview from the settings screen. */
        fun preview(context: Context, uri: String) =
            start(context, uri, tr("Adhan preview", "আজান প্রিভিউ"), tr("Tap Stop to end", "বন্ধ করতে Stop চাপুন"))
    }
}
