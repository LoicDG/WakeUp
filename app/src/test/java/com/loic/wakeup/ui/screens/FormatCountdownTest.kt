package com.loic.wakeup.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
