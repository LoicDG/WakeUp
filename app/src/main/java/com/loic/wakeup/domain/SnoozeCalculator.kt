package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity

/** Outcome of a snooze request. */
sealed interface SnoozeDecision {
    /** Snooze allowed: persist [newSnoozeCount] and schedule a re-ring at [triggerAtMillis]. */
    data class Reschedule(val newSnoozeCount: Int, val triggerAtMillis: Long) : SnoozeDecision

    /** Max snoozes already used: do not reschedule. */
    data object MaxReached : SnoozeDecision
}

/**
 * Pure decision logic for snoozing an alarm. Kept free of Android dependencies so the
 * re-ring timing — the part that previously lived in [AlarmService.handleSnooze] and was
 * driven by an in-process `delay()` — can be unit tested.
 */
object SnoozeCalculator {
    fun decide(alarm: AlarmEntity, nowMillis: Long): SnoozeDecision {
        if (alarm.snoozeCount >= alarm.maxSnoozes) return SnoozeDecision.MaxReached
        return SnoozeDecision.Reschedule(
            newSnoozeCount = alarm.snoozeCount + 1,
            triggerAtMillis = nowMillis + alarm.snoozeDurationSeconds * 1000L,
        )
    }
}
