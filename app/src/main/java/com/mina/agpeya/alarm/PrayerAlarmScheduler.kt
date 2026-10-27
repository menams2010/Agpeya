package com.mina.agpeya.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mina.agpeya.data.PrayerPreference
import java.util.Calendar

object PrayerAlarmScheduler {
    fun scheduleAll(context: Context, settings: List<PrayerPreference>) {
        settings.forEach { preference ->
            if (preference.enabled) scheduleNext(context, preference)
            else cancel(context, preference.key)
        }
    }

    fun scheduleNext(context: Context, preference: PrayerPreference) {
        val (hour, minute) = preference.time.split(':').map(String::toInt)
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = alarmIntent(context, preference.key)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, prayerKey: String) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(alarmIntent(context, prayerKey))
    }

    private fun alarmIntent(context: Context, prayerKey: String): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java)
            .putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_KEY, prayerKey)
        return PendingIntent.getBroadcast(
            context,
            prayerKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
