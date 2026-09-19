package com.loic.wakeup.domain

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.TagFailsafeEntity
import com.loic.wakeup.receiver.AlarmReceiver
import com.loic.wakeup.receiver.AlarmReminderReceiver
import com.loic.wakeup.receiver.FailsafeReceiver

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmEntity) {
        if (!alarm.enabled) return
        val nextAt = NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask)
        val operation = buildPendingIntent(alarm.id)
        val showIntent = buildPendingIntent(alarm.id)
        alarmManager.setAlarmClock(AlarmClockInfo(nextAt, showIntent), operation)
        scheduleReminder(alarm.id, nextAt)
    }

    /**
     * Schedules the pre-alarm reminder notification 30 minutes before [triggerAtMillis].
     * Uses setExactAndAllowWhileIdle (not setAlarmClock) so it never hijacks the system's
     * user-facing "next alarm" indicator. Skipped if the reminder time is already in the past
     * (alarm set <30 min out, or rebooted inside the window).
     */
    private fun scheduleReminder(alarmId: Int, triggerAtMillis: Long) {
        val reminderAt = triggerAtMillis - REMINDER_LEAD_MILLIS
        if (reminderAt <= System.currentTimeMillis()) return
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminderAt,
            buildReminderPendingIntent(alarmId)
        )
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
        alarmManager.cancel(buildReminderPendingIntent(alarmId))
    }

    /**
     * Queues a tag failsafe's next daily location check (see [FailsafeReceiver]). Like the
     * reminder, it uses setExactAndAllowWhileIdle so it never shows up as the "next alarm".
     * A failsafe that is off or has no spot yet is cancelled instead.
     */
    fun scheduleFailsafe(failsafe: TagFailsafeEntity) {
        if (!failsafe.enabled || failsafe.spot == null) {
            cancelFailsafe(failsafe.tagUid)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            NextTriggerCalculator.next(failsafe.hour, failsafe.minute, 0),
            buildFailsafePendingIntent(failsafe.tagUid)
        )
    }

    fun cancelFailsafe(tagUid: String) {
        alarmManager.cancel(buildFailsafePendingIntent(tagUid))
    }

    private fun buildFailsafePendingIntent(tagUid: String): PendingIntent {
        // Failsafes are keyed by the tag's string UID rather than an int id, so the UID goes in
        // the data URI — which is part of PendingIntent identity — instead of a request-code offset.
        val intent = Intent(context, FailsafeReceiver::class.java).apply {
            action = FailsafeReceiver.ACTION_CHECK
            data = Uri.fromParts(FAILSAFE_URI_SCHEME, tagUid, null)
            putExtra(FailsafeReceiver.EXTRA_TAG_UID, tagUid)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildReminderPendingIntent(alarmId: Int): PendingIntent {
        val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = AlarmReminderReceiver.ACTION_SHOW_REMINDER
            putExtra("alarmId", alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE_OFFSET + alarmId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
        const val REMINDER_REQUEST_CODE_OFFSET = 300_000
        const val REMINDER_LEAD_MILLIS = 30L * 60L * 1000L
        const val FAILSAFE_URI_SCHEME = "wakeup-failsafe"
    }
}
