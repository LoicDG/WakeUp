package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FailsafePolicyTest {

    // --- isAway ---

    @Test
    fun clearlyOutsideRadius_isAway() {
        assertTrue(FailsafePolicy.isAway(distanceMeters = 150.0, accuracyMeters = 20.0))
    }

    @Test
    fun insideRadius_isNotAway() {
        assertFalse(FailsafePolicy.isAway(distanceMeters = 60.0, accuracyMeters = 5.0))
    }

    @Test
    fun exactlyOnRadius_isNotAway() {
        assertFalse(FailsafePolicy.isAway(distanceMeters = 100.0, accuracyMeters = 0.0))
    }

    @Test
    fun outsideButWithinFixAccuracy_isNotAway() {
        // 150 m away, but the fix could be off by 60 m — maybe only 90 m away. Keep the alarm.
        assertFalse(FailsafePolicy.isAway(distanceMeters = 150.0, accuracyMeters = 60.0))
    }

    @Test
    fun farAwayEvenWithCoarseFix_isAway() {
        assertTrue(FailsafePolicy.isAway(distanceMeters = 50_000.0, accuracyMeters = 2_000.0))
    }

    // --- alarmsToDeactivate ---

    private val tag = "a1b2c3d4"
    private val otherTag = "deadbeef"

    /** Friday 2026-09-18 22:00 local, the moment the 22:00 check fires. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 18, 22, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val nextCheck = NextTriggerCalculator.next(22, 0, 0, now)

    @Test
    fun globalTagAlarmRingingBeforeNextCheck_isDeactivated() {
        val alarm = alarm(id = 1, hour = 7)
        assertEquals(listOf(alarm), deactivate(listOf(alarm)))
    }

    @Test
    fun customTagAlarmMatchingTag_isDeactivated() {
        val alarm = alarm(id = 1, hour = 7, nfcTagUid = tag)
        assertEquals(listOf(alarm), deactivate(listOf(alarm), globalTagUid = otherTag))
    }

    @Test
    fun alarmOfAnotherTag_isLeftAlone() {
        val custom = alarm(id = 1, hour = 7, nfcTagUid = otherTag)
        val global = alarm(id = 2, hour = 7)
        assertTrue(deactivate(listOf(custom, global), globalTagUid = otherTag).isEmpty())
    }

    @Test
    fun noTagAlarm_isLeftAlone() {
        assertTrue(deactivate(listOf(alarm(id = 1, hour = 7, dismissWithoutTag = true))).isEmpty())
    }

    @Test
    fun disabledAlarm_isLeftAlone() {
        assertTrue(deactivate(listOf(alarm(id = 1, hour = 7, enabled = false))).isEmpty())
    }

    @Test
    fun ringingAlarm_isLeftAlone() {
        assertTrue(deactivate(listOf(alarm(id = 1, hour = 7)), ringingAlarmId = 1).isEmpty())
    }

    @Test
    fun alarmAfterNextCheck_isLeftForThatCheck() {
        // Friday-night check: Monday's weekday alarm is two checks away, so Sunday's check decides it.
        val weekdays = (1..5).fold(0) { mask, bit -> mask or (1 shl bit) }
        assertTrue(deactivate(listOf(alarm(id = 1, hour = 7, daysMask = weekdays))).isEmpty())
    }

    @Test
    fun alarmLaterTonight_isDeactivated() {
        val alarm = alarm(id = 1, hour = 23)
        assertEquals(listOf(alarm), deactivate(listOf(alarm)))
    }

    private fun deactivate(
        alarms: List<AlarmEntity>,
        globalTagUid: String? = tag,
        ringingAlarmId: Int? = null,
    ) = FailsafePolicy.alarmsToDeactivate(tag, globalTagUid, alarms, ringingAlarmId, now, nextCheck)

    private fun alarm(
        id: Int,
        hour: Int,
        daysMask: Int = 0,
        enabled: Boolean = true,
        nfcTagUid: String? = null,
        dismissWithoutTag: Boolean = false,
    ) = AlarmEntity(
        id = id,
        hour = hour,
        minute = 0,
        daysMask = daysMask,
        enabled = enabled,
        nfcTagUid = nfcTagUid,
        dismissWithoutTag = dismissWithoutTag,
    )
}
