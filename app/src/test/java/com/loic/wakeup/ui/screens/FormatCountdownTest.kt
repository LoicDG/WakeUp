package com.loic.wakeup.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import com.loic.wakeup.data.AlarmEntity
import java.util.Calendar

class FormatCountdownTest {

    // formatDuration tests — pure millis-to-string conversion

    @Test
    fun formatDuration_hoursAndMinutes() {
        val millis = (6 * 60 + 30) * 60_000L
        assertEquals("Rings in 6h 30min", formatDuration(millis))
    }

    @Test
    fun formatDuration_minutesOnly() {
        val millis = 45 * 60_000L
        assertEquals("Rings in 45min", formatDuration(millis))
    }

    @Test
    fun formatDuration_exactlyOneHour() {
        val millis = 60 * 60_000L
        assertEquals("Rings in 1h 0min", formatDuration(millis))
    }

    @Test
    fun formatDuration_zero() {
        assertEquals("Rings in 0min", formatDuration(0L))
    }

    @Test
    fun formatDuration_negative_clampsToZero() {
        assertEquals("Rings in 0min", formatDuration(-5_000L))
    }

    @Test
    fun nextRingAfterSkippedOccurrence_beforeMorningAlarm_returnsFollowingDay() {
        val wednesdayBeforeAlarm = millisFor(
            year = 2026,
            month = Calendar.JANUARY,
            day = 7,
            hour = 1,
            minute = 0,
        )
        val alarm = AlarmEntity(
            id = 1,
            hour = 7,
            minute = 30,
            daysMask = (1 shl 3) or (1 shl 4), // Wednesday and Thursday
            enabled = false,
        )

        val nextActiveRing = nextRingAfterSkippedOccurrence(alarm, wednesdayBeforeAlarm)
        val cal = Calendar.getInstance().apply { timeInMillis = nextActiveRing }

        assertEquals(Calendar.THURSDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun nextRingAfter_fromTemporaryDisableTime_returnsFollowingOccurrence() {
        val skippedWednesdayAlarm = millisFor(
            year = 2026,
            month = Calendar.JANUARY,
            day = 7,
            hour = 7,
            minute = 30,
        )
        val alarm = AlarmEntity(
            id = 1,
            hour = 7,
            minute = 30,
            daysMask = (1 shl 3) or (1 shl 4), // Wednesday and Thursday
            enabled = false,
            temporaryDisabledUntilMillis = skippedWednesdayAlarm,
        )

        val nextActiveRing = nextRingAfter(alarm, alarm.temporaryDisabledUntilMillis!!)
        val cal = Calendar.getInstance().apply { timeInMillis = nextActiveRing }

        assertEquals(Calendar.THURSDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    private fun millisFor(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
