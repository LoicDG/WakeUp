package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeCalculatorTest {

    @Test
    fun firstSnooze_reschedulesAtNowPlusDuration() {
        val alarm = alarm(snoozeCount = 0, maxSnoozes = 3, snoozeDurationSeconds = 20)

        val decision = SnoozeCalculator.decide(alarm, nowMillis = 1_000_000L)

        assertEquals(
            SnoozeDecision.Reschedule(newSnoozeCount = 1, triggerAtMillis = 1_020_000L),
            decision,
        )
    }

    @Test
    fun lastAllowedSnooze_reschedulesAndReachesMax() {
        val alarm = alarm(snoozeCount = 2, maxSnoozes = 3, snoozeDurationSeconds = 60)

        val decision = SnoozeCalculator.decide(alarm, nowMillis = 5_000L)

        assertEquals(
            SnoozeDecision.Reschedule(newSnoozeCount = 3, triggerAtMillis = 65_000L),
            decision,
        )
    }

    @Test
    fun maxSnoozesReached_doesNotReschedule() {
        val alarm = alarm(snoozeCount = 3, maxSnoozes = 3, snoozeDurationSeconds = 20)

        val decision = SnoozeCalculator.decide(alarm, nowMillis = 1_000_000L)

        assertEquals(SnoozeDecision.MaxReached, decision)
    }

    private fun alarm(snoozeCount: Int, maxSnoozes: Int, snoozeDurationSeconds: Int) = AlarmEntity(
        hour = 7,
        minute = 0,
        snoozeCount = snoozeCount,
        maxSnoozes = maxSnoozes,
        snoozeDurationSeconds = snoozeDurationSeconds,
    )
}
