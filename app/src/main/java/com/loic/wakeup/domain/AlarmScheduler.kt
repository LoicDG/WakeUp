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

    fun cancel(alarmId: Int) {
        alarmManager.cancel(buildPendingIntent(alarmId))
    }

    private fun buildPendingIntent(alarmId: Int, isSnooze: Boolean = false): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarmId)
            putExtra("isSnooze", isSnooze)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
