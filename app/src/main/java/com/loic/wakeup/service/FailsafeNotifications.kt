package com.loic.wakeup.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.loic.wakeup.MainActivity
import com.loic.wakeup.R
import com.loic.wakeup.WakeUpApp
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.SettingsStore
import com.loic.wakeup.ui.components.formatClock
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Notifications for the location failsafe: the silent "checking" one the foreground service
 * needs, plus one outcome notification per tag (keyed by the tag UID as notification tag, so a
 * newer outcome replaces an older one for the same tag).
 */
object FailsafeNotifications {
    const val CHECKING_NOTIFICATION_ID = 1002
    private const val RESULT_NOTIFICATION_ID = 1003

    fun checking(context: Context): Notification =
        NotificationCompat.Builder(context, WakeUpApp.FAILSAFE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(context.getString(R.string.failsafe_checking_title))
            .setContentText(context.getString(R.string.failsafe_checking_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()

    fun showDeactivated(context: Context, tagUid: String, distanceMeters: Double, alarms: List<AlarmEntity>) {
        val use24Hour = SettingsStore.use24Hour.value
        val alarmList = alarms.joinToString(", ") { alarm ->
            val time = formatClock(alarm.hour, alarm.minute, use24Hour)
            if (alarm.label.isNotBlank()) "$time (${alarm.label})" else time
        }
        post(
            context,
            tagUid,
            context.getString(R.string.failsafe_deactivated_title),
            context.getString(
                R.string.failsafe_deactivated_text,
                formatDistance(distanceMeters), tagUid.take(4), tagUid.takeLast(4), alarmList,
            ),
        )
    }

    fun showNoLocation(context: Context, tagUid: String) = post(
        context,
        tagUid,
        context.getString(R.string.failsafe_no_location_title),
        context.getString(R.string.failsafe_no_location_text, tagUid.take(4), tagUid.takeLast(4)),
    )

    fun showPermissionMissing(context: Context, tagUid: String) = post(
        context,
        tagUid,
        context.getString(R.string.failsafe_permission_title),
        context.getString(R.string.failsafe_permission_text, tagUid.take(4), tagUid.takeLast(4)),
    )

    private fun post(context: Context, tagUid: String, title: String, text: String) {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, WakeUpApp.FAILSAFE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(tagUid, RESULT_NOTIFICATION_ID, notification)
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m"
        else String.format(Locale.getDefault(), "%.1f km", meters / 1000)
}
