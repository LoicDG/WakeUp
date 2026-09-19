package com.loic.wakeup.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.loic.wakeup.data.AlarmDatabase
import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.AlarmRepository
import com.loic.wakeup.data.NfcTagStore
import com.loic.wakeup.data.TagFailsafeEntity
import com.loic.wakeup.data.TagFailsafeRepository
import com.loic.wakeup.domain.AlarmDeactivator
import com.loic.wakeup.domain.AlarmScheduler
import com.loic.wakeup.domain.CurrentLocation
import com.loic.wakeup.domain.FailsafePolicy
import com.loic.wakeup.domain.GeoPoint
import com.loic.wakeup.domain.NextTriggerCalculator
import com.loic.wakeup.domain.spot
import com.loic.wakeup.receiver.FailsafeReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Short-lived location-type foreground service that runs one failsafe check per start: gets a
 * fresh fix and, if the device is clearly away from the tag's spot, deactivates the tag's alarms
 * that would ring before the next check (see [FailsafePolicy]) and says so in a notification.
 * Started by [FailsafeReceiver]. Stops itself once every in-flight check is done.
 */
class FailsafeService : Service() {

    companion object {
        private const val TAG = "FailsafeService"

        /** The tag's armed alarms that this check governs, i.e. that ring before the next check. */
        suspend fun alarmsToDeactivate(context: Context, failsafe: TagFailsafeEntity): List<AlarmEntity> {
            val now = System.currentTimeMillis()
            return FailsafePolicy.alarmsToDeactivate(
                tagUid = failsafe.tagUid,
                globalTagUid = NfcTagStore(context).getUid(),
                alarms = AlarmRepository(AlarmDatabase.getInstance(context).alarmDao()).getAll(),
                ringingAlarmId = AlarmService.runningAlarmId.takeIf { AlarmService.isRunning },
                nowMillis = now,
                nextCheckAtMillis = NextTriggerCalculator.next(failsafe.hour, failsafe.minute, 0, now),
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Checks for several tags can overlap (same check time); only touched on the main thread.
    private var activeChecks = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground promptly after startForegroundService, whatever happens next.
        try {
            ServiceCompat.startForeground(
                this,
                FailsafeNotifications.CHECKING_NOTIFICATION_ID,
                FailsafeNotifications.checking(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // E.g. location permission revoked since the receiver checked it.
            Log.w(TAG, "Couldn't start the failsafe check in the foreground", e)
            if (activeChecks == 0) stopSelf()
            return START_NOT_STICKY
        }

        val tagUid = intent?.getStringExtra(FailsafeReceiver.EXTRA_TAG_UID)
        if (tagUid == null) {
            if (activeChecks == 0) stopSelf()
            return START_NOT_STICKY
        }
        activeChecks++
        scope.launch {
            try {
                withContext(Dispatchers.IO) { runCheck(tagUid) }
            } catch (e: Exception) {
                Log.w(TAG, "Failsafe check for tag $tagUid failed", e)
            } finally {
                if (--activeChecks == 0) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runCheck(tagUid: String) {
        val db = AlarmDatabase.getInstance(this)
        val failsafe = TagFailsafeRepository(db.tagFailsafeDao()).getByUid(tagUid) ?: return
        val spot = failsafe.spot ?: return
        if (!failsafe.enabled) return

        val fix = CurrentLocation.get(this)
        if (fix == null) {
            // Can't tell where we are — leave the alarms armed rather than silence them blindly.
            FailsafeNotifications.showNoLocation(this, tagUid)
            return
        }
        val distance = GeoPoint(fix.latitude, fix.longitude).distanceTo(spot)
        val accuracy = if (fix.hasAccuracy()) fix.accuracy.toDouble() else 0.0
        if (!FailsafePolicy.isAway(distance, accuracy)) return

        // Re-read after the fix: an alarm may have been toggled while it was in flight.
        val targets = alarmsToDeactivate(this, failsafe)
        if (targets.isEmpty()) return
        val repo = AlarmRepository(db.alarmDao())
        val scheduler = AlarmScheduler(this)
        targets.forEach { AlarmDeactivator.deactivate(it, repo, scheduler) }
        FailsafeNotifications.showDeactivated(this, tagUid, distance, targets)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
