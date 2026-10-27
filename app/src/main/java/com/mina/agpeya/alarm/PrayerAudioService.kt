package com.mina.agpeya.alarm

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.os.Build
import androidx.core.app.ServiceCompat

class PrayerAudioService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayerKey = intent?.getStringExtra(PrayerAlarmReceiver.EXTRA_PRAYER_KEY)
        if (prayerKey == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val volume = intent.getFloatExtra(EXTRA_VOLUME, 0.7f).coerceIn(0f, 1f)
        val soundUri = intent.getStringExtra(EXTRA_SOUND_URI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                PrayerNotification.NOTIFICATION_ID,
                PrayerNotification.build(this, prayerKey),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            ServiceCompat.startForeground(
                this,
                PrayerNotification.NOTIFICATION_ID,
                PrayerNotification.build(this, prayerKey),
                0,
            )
        }
        startAudio(volume, soundUri)
        return START_NOT_STICKY
    }

    private fun startAudio(volume: Float, soundUri: String?) {
        player?.release()
        val alarmUri = soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        if (alarmUri == null) {
            Log.e(TAG, "No system alarm sound is configured")
            stopSelf()
            return
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@PrayerAudioService, alarmUri)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to play the configured alarm sound", error)
            stopSelf()
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(android.app.NotificationManager::class.java)
            .cancel(PrayerNotification.NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PRAYER_KEY = "prayer_key"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_SOUND_URI = "sound_uri"
        private const val TAG = "PrayerAudioService"
    }
}

class StopPrayerAudioReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent?) {
        context.stopService(Intent(context, PrayerAudioService::class.java))
        context.getSystemService(android.app.NotificationManager::class.java)
            .cancel(PrayerNotification.NOTIFICATION_ID)
    }
}
