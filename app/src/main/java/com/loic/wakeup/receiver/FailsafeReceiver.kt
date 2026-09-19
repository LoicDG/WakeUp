package com.loic.wakeup.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.data.TagFailsafeRepository
import com.loic.wakeup.domain.AlarmScheduler
import com.loic.wakeup.domain.LocationAccess
import com.loic.wakeup.domain.registeredTagUids
import com.loic.wakeup.domain.spot
import com.loic.wakeup.service.FailsafeNotifications
import com.loic.wakeup.service.FailsafeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager at a tag failsafe's daily check time (see
 * [AlarmScheduler.scheduleFailsafe]). Queues tomorrow's check, then — only if one of the tag's
 * alarms would ring before it — hands the location check to [FailsafeService]. A receiver can't
 * reliably wait out a GPS fix, and Doze withholds location from background apps, so the check
 * itself runs in a short location-type foreground service; the exact alarm is what allows
 * starting it from the background.
 */
class FailsafeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK) return
        val tagUid = intent.getStringExtra(EXTRA_TAG_UID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AlarmDatabase.getInstance(context)
                val failsafes = TagFailsafeRepository(db.tagFailsafeDao())
                val failsafe = failsafes.getByUid(tagUid) ?: return@launch
                val alarms = AlarmRepository(db.alarmDao()).getAll()
                // The tag was removed or replaced since this check was queued: drop its failsafe.
                if (tagUid !in registeredTagUids(NfcTagStore(context).getUid(), alarms)) {
                    failsafes.delete(tagUid)
                    return@launch
                }
                if (!failsafe.enabled || failsafe.spot == null) return@launch

                // Queue tomorrow's check first so one failed check never breaks the daily chain.
                AlarmScheduler(context).scheduleFailsafe(failsafe)

                // Nothing of this tag's rings before the next check — no need to locate anyone.
                if (FailsafeService.alarmsToDeactivate(context, failsafe).isEmpty()) return@launch
                if (!LocationAccess.hasBackground(context)) {
                    FailsafeNotifications.showPermissionMissing(context, tagUid)
                    return@launch
                }
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FailsafeService::class.java).putExtra(EXTRA_TAG_UID, tagUid),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.loic.wakeup.action.FAILSAFE_CHECK"
        const val EXTRA_TAG_UID = "tagUid"
    }
}
