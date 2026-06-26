package com.loic.wakeup.domain

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.receiver.AlarmReceiver

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) return
        val nextAt = NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask)
        val operation = buildPendingIntent(alarm.id)
        val showIntent = buildPendingIntent(alarm.id)
        alarmManager.setAlarmClock(AlarmClockInfo(nextAt, showIntent), operation)
    }

    fun scheduleAt(alarm: AlarmEntity, atMillis: Long, isSnooze: Boolean = false) {
        val operation = buildPendingIntent(alarm.id, isSnooze)
        val showIntent = buildPendingIntent(alarm.id, isSnooze)
        alarmManager.setAlarmClock(AlarmClockInfo(atMillis, showIntent), operation)
    }

    fun scheduleReenable(alarm: AlarmEntity, atMillis: Long) {
        val operation = buildReenablePendingIntent(alarm.id)
        val showIntent = buildReenablePendingIntent(alarm.id)
        alarmManager.setAlarmClock(AlarmClockInfo(atMillis, showIntent), operation)
    }

    /** Cancels only a pending snooze re-ring, leaving the alarm's regular schedule intact. */
    fun cancelSnooze(alarmId: Int) {
        alarmManager.cancel(buildPendingIntent(alarmId, isSnooze = true))
    }

    fun cancel(alarmId: Int) {
        alarmManager.cancel(buildPendingIntent(alarmId))
        alarmManager.cancel(buildPendingIntent(alarmId, isSnooze = true))
        alarmManager.cancel(buildReenablePendingIntent(alarmId))
    }

    private fun buildPendingIntent(alarmId: Int, isSnooze: Boolean = false): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarmId)
            putExtra("isSnooze", isSnooze)
        }
        // Snooze re-rings use a distinct request code so they never collide with — or
        // accidentally cancel — the alarm's regular next-occurrence PendingIntent.
        val requestCode = if (isSnooze) SNOOZE_REQUEST_CODE_OFFSET + alarmId else alarmId
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildReenablePendingIntent(alarmId: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_REENABLE_ALARM
            putExtra("alarmId", alarmId)
            putExtra("reenableOnly", true)
        }
        return PendingIntent.getBroadcast(
            context,
            REENABLE_REQUEST_CODE_OFFSET + alarmId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val ACTION_REENABLE_ALARM = "com.loic.wakeup.action.REENABLE_ALARM"
        const val REENABLE_REQUEST_CODE_OFFSET = 100_000
        const val SNOOZE_REQUEST_CODE_OFFSET = 200_000
    }
}
