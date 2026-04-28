package com.loic.wakeup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.domain.AlarmScheduler
import com.loic.wakeup.domain.canActivateWithGlobalTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(AlarmDatabase.getInstance(context).alarmDao())
                val scheduler = AlarmScheduler(context)
                repo.getAllEnabled().forEach { scheduler.schedule(it) }
                repo.getAllTemporarilyDisabled().forEach { alarm ->
                    val reenableAt = alarm.temporaryDisabledUntilMillis
                    if (reenableAt == null || reenableAt <= System.currentTimeMillis()) {
                        if (!alarm.canActivateWithGlobalTag(NfcTagStore(context).getUid())) {
                            repo.setEnabled(alarm.id, false)
                            return@forEach
                        }
                        repo.clearTemporaryDisableAndEnable(alarm.id)
                        scheduler.schedule(alarm.copy(enabled = true, temporaryDisabledUntilMillis = null))
                    } else {
                        scheduler.scheduleReenable(alarm, reenableAt)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
