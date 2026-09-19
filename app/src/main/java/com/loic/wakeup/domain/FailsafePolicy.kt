package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.TagFailsafeEntity

/** The tag's chosen spot, or null until the user has picked one. */
val TagFailsafeEntity.spot: GeoPoint?
    get() = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

/**
 * Pure decision logic for the per-tag location failsafe.
 *
 * The problem it solves: alarms can only be dismissed by scanning their NFC tag, so an alarm
 * that rings while you're away from the tag (travelling, sleeping elsewhere) can't be stopped.
 * Each tag can have a daily check time and a spot; at the check the device is located, and if
 * it's clearly outside [RADIUS_METERS] of the spot the tag's upcoming alarms are deactivated.
 *
 * Each check only governs the alarms that would ring *before the next check* — later ones are
 * left for that check to decide. So a Friday-night check away from home never silences
 * Monday's alarm: Sunday night's check (back home) gets the final say.
 */
object FailsafePolicy {

    const val RADIUS_METERS = 100.0

    /**
     * True only when the device is certainly outside the radius — the whole accuracy circle of
     * the fix lies beyond it. A borderline or noisy fix counts as "at the spot": wrongly
     * silencing an alarm is worse than wrongly leaving one armed.
     */
    fun isAway(distanceMeters: Double, accuracyMeters: Double): Boolean =
        distanceMeters - accuracyMeters.coerceAtLeast(0.0) > RADIUS_METERS

    /**
     * Alarms dismissed by [tagUid] that are armed and would ring before [nextCheckAtMillis].
     * The currently ringing (or snoozed) alarm is left alone — deactivating it would cancel its
     * pending snooze re-ring out from under the ringing screen.
     */
    fun alarmsToDeactivate(
        tagUid: String,
        globalTagUid: String?,
        alarms: List<AlarmEntity>,
        ringingAlarmId: Int?,
        nowMillis: Long,
        nextCheckAtMillis: Long,
    ): List<AlarmEntity> = alarms.filter { alarm ->
        alarm.enabled &&
            alarm.id != ringingAlarmId &&
            alarm.effectiveTagUid(globalTagUid) == tagUid &&
            NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask, nowMillis) <= nextCheckAtMillis
    }
}
