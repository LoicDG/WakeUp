package com.loic.wakeup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.domain.AlarmScheduler
import com.loic.wakeup.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("alarmId", -1)
        if (alarmId == -1) return
        val isSnooze = intent.getBooleanExtra("isSnooze", false)

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeUp:AlarmReceiver")
        wl.acquire(10_000L)

        // Launch the ringing activity immediately while still in the alarm delivery window.
        // Android 10+ allows background activity launches from BroadcastReceiver.onReceive()
        // when triggered by AlarmManager — this window closes once onReceive() returns.
        val ringingIntent = Intent(context, com.loic.wakeup.ui.screens.AlarmRingingActivity::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(ringingIntent)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("alarmId", alarmId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AlarmDatabase.getInstance(context).alarmDao())
                val alarm = repo.getById(alarmId) ?: return@launch
                // Only reset snooze count for fresh (non-snooze) rings
                if (!isSnooze) repo.setSnoozeCount(alarmId, 0)
                // Re-schedule if repeating
                if (alarm.daysMask != 0) {
                    AlarmScheduler(context).schedule(alarm)
                } else {
                    repo.setEnabled(alarmId, false)
                }
            } finally {
                wl.release()
                pendingResult.finish()
            }
        }
    }
}
