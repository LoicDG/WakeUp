package com.loic.wakeup.domain

import java.util.Calendar

/**
 * Computes the next trigger time in epoch millis.
 *
 * [daysMask] bitmask: bit 0 = Sunday, bit 6 = Saturday (same as Calendar.DAY_OF_WEEK mapped).
 * If daysMask == 0 the alarm is one-shot: fires at the next occurrence of hour:minute
 * (today if the time hasn't passed yet, else tomorrow).
 */
object NextTriggerCalculator {

    fun next(hour: Int, minute: Int, daysMask: Int, fromMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)

        if (cal.timeInMillis <= fromMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (daysMask == 0) return cal.timeInMillis

        // Find the next matching day-of-week (max 7 days search)
        repeat(7) {
            val calDow = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2 … Sat=7
            val bit = calDowToBit(calDow)
            if (daysMask and (1 shl bit) != 0) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** Maps Calendar.DAY_OF_WEEK (Sun=1…Sat=7) to bit index (Sun=0…Sat=6). */
    private fun calDowToBit(calDow: Int): Int = when (calDow) {
        Calendar.SUNDAY    -> 0
        Calendar.MONDAY    -> 1
        Calendar.TUESDAY   -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY  -> 4
        Calendar.FRIDAY    -> 5
        else               -> 6 // Saturday
    }
}
