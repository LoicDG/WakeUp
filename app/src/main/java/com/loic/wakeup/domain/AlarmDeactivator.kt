package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity
import com.loic.wakeup.data.AlarmRepository

/**
 * "Deactivate" as the app means it: a recurring alarm skips only its next occurrence (temporary
 * disable + a reboot-safe re-enable, so it re-arms itself afterwards); a one-shot alarm is turned
 * off. Shared by the reminder notification's action and the location failsafe.
 */
object AlarmDeactivator {
    suspend fun deactivate(alarm: AlarmEntity, repo: AlarmRepository, scheduler: AlarmScheduler) {
        if (alarm.daysMask != 0) {
            val reenableAt = NextTriggerCalculator.next(alarm.hour, alarm.minute, alarm.daysMask)
            repo.setTemporarilyDisabledUntil(alarm.id, reenableAt)
            scheduler.cancel(alarm.id)
            scheduler.scheduleReenable(alarm, reenableAt)
        } else {
            repo.setEnabled(alarm.id, false)
            scheduler.cancel(alarm.id)
        }
    }
}
