package com.loic.wakeup.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.loic.wakeup.R
import com.loic.wakeup.WakeUpApp
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.domain.AlarmScheduler
import com.loic.wakeup.domain.NextTriggerCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Posts the pre-alarm reminder notification 30 minutes before an alarm rings and
 * handles its "deactivate" action.
 *
 * - [ACTION_SHOW_REMINDER] is fired by AlarmManager (scheduled in [AlarmScheduler.schedule]).
 * - [ACTION_DEACTIVATE] is fired by tapping the notification action:
 *     - Recurring alarm → skip only today's occurrence (reuses the temporary-disable +
 *       scheduleReenable flow, which is reboot-safe); the alarm auto re-arms for the next day.
 *     - One-shot alarm → disable it.
 */
class AlarmReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarmId", -1)
        if (alarmId == -1) return
        when (intent.action) {
            ACTION_SHOW_REMINDER -> showReminder(context, alarmId)
            ACTION_DEACTIVATE -> deactivate(context, alarmId)
        }
    }

    private fun showReminder(context: Context, alarmId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AlarmDatabase.getInstance(context).alarmDao())
                val alarm = repo.getById(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch
                postNotification(context, alarm)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun deactivate(context: Context, alarmId: Int) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AlarmDatabase.getInstance(context).alarmDao())
                val alarm = repo.getById(alarmId) ?: return@launch
                val scheduler = AlarmScheduler(context)
                if (alarm.daysMask != 0) {
                    // Recurring: skip only today's occurrence; auto re-arm for the next.
                    val reenableAt = NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask)
                    repo.setTemporarilyDisabledUntil(alarmId, reenableAt)
                    scheduler.cancel(alarmId)
                    scheduler.scheduleReenable(alarm, reenableAt)
                } else {
                    repo.setEnabled(alarmId, false)
                    scheduler.cancel(alarmId)
                }
                cancelNotification(context, alarmId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postNotification(context: Context, alarm: AlarmEntity) {
        val timeText = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        val contentText = if (alarm.label.isNotBlank()) "${alarm.label} • $timeText" else timeText

        val skipTodayOnly = alarm.daysMask != 0
        val actionLabel = context.getString(
            if (skipTodayOnly) R.string.reminder_action_skip_today else R.string.reminder_action_turn_off
        )
        val deactivateIntent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = ACTION_DEACTIVATE
            putExtra("alarmId", alarm.id)
        }
        val deactivatePi = PendingIntent.getBroadcast(
            context,
            DEACTIVATE_REQUEST_CODE_OFFSET + alarm.id,
            deactivateIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, WakeUpApp.REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, actionLabel, deactivatePi)
            .build()

        notificationManager(context).notify(REMINDER_NOTIFICATION_BASE + alarm.id, notification)
    }

    private fun cancelNotification(context: Context, alarmId: Int) {
        notificationManager(context).cancel(REMINDER_NOTIFICATION_BASE + alarmId)
    }

    private fun notificationManager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val ACTION_SHOW_REMINDER = "com.loic.wakeup.action.SHOW_REMINDER"
        const val ACTION_DEACTIVATE = "com.loic.wakeup.action.DEACTIVATE_FROM_REMINDER"
        const val REMINDER_NOTIFICATION_BASE = 2000
        private const val DEACTIVATE_REQUEST_CODE_OFFSET = 400_000
    }
}
