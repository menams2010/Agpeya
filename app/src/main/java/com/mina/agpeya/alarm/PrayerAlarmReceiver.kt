package com.mina.agpeya.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.mina.agpeya.data.PrayerCatalog
import com.mina.agpeya.data.PrayerSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra(EXTRA_PRAYER_KEY) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val preference = PrayerSettingsRepository(context).settings.first()
                    .firstOrNull { it.key == prayerKey }
                    ?: return@launch
                if (!preference.enabled) return@launch

                PrayerNotification.show(context, prayerKey)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PrayerAudioService::class.java)
                        .putExtra(PrayerAudioService.EXTRA_PRAYER_KEY, prayerKey)
                        .putExtra(PrayerAudioService.EXTRA_VOLUME, preference.volume)
                        .putExtra(PrayerAudioService.EXTRA_SOUND_URI, preference.soundUri),
                )
                PrayerAlarmScheduler.scheduleNext(context, preference)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to deliver prayer reminder for $prayerKey", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_PRAYER_KEY = "prayer_key"
        private const val TAG = "PrayerAlarmReceiver"
    }
}

object PrayerNotification {
    const val CHANNEL_ID = "prayer_reminders"
    const val NOTIFICATION_ID = 7001

    fun show(context: Context, prayerKey: String) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    "تنبيهات الصلوات",
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
        manager.notify(NOTIFICATION_ID, build(context, prayerKey))
    }

    fun build(context: Context, prayerKey: String): android.app.Notification {
        val openIntent = android.app.PendingIntent.getActivity(
            context,
            prayerKey.hashCode(),
            Intent(context, com.mina.agpeya.MainActivity::class.java)
                .putExtra(EXTRA_OPEN_PRAYER, prayerKey),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = android.app.PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, StopPrayerAudioReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(PrayerCatalog.titleFor(prayerKey))
            .setContentText("حان موعد الصلاة")
            .setContentIntent(openIntent)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الصوت", stopIntent)
            .build()
    }

    const val EXTRA_OPEN_PRAYER = "open_prayer"
}
