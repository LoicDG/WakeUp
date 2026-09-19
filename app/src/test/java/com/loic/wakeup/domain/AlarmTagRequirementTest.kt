package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTagRequirementTest {

    @Test
    fun globalTagAlarm_requiresGlobalTag() {
        val alarm = alarm(nfcTagUid = null, dismissWithoutTag = false)

        assertTrue(alarm.requiresGlobalTag())
        assertFalse(alarm.canActivateWithGlobalTag(null))
        assertTrue(alarm.canActivateWithGlobalTag("a1b2c3d4"))
    }

    @Test
    fun customTagAlarm_doesNotRequireGlobalTag() {
        val alarm = alarm(nfcTagUid = "deadbeef", dismissWithoutTag = false)

        assertFalse(alarm.requiresGlobalTag())
        assertTrue(alarm.canActivateWithGlobalTag(null))
    }

    @Test
    fun noTagAlarm_doesNotRequireAnyTag() {
        val alarm = alarm(nfcTagUid = null, dismissWithoutTag = true)

        assertFalse(alarm.requiresGlobalTag())
        assertTrue(alarm.canActivateWithGlobalTag(null))
    }

    @Test
    fun effectiveTagUid_prefersCustomTagOverGlobal() {
        assertEquals("deadbeef", alarm(nfcTagUid = "deadbeef", dismissWithoutTag = false).effectiveTagUid("a1b2c3d4"))
        assertEquals("a1b2c3d4", alarm(nfcTagUid = null, dismissWithoutTag = false).effectiveTagUid("a1b2c3d4"))
        assertNull(alarm(nfcTagUid = null, dismissWithoutTag = false).effectiveTagUid(null))
        assertNull(alarm(nfcTagUid = null, dismissWithoutTag = true).effectiveTagUid("a1b2c3d4"))
    }

    @Test
    fun registeredTagUids_listsGlobalThenCustomTagsOnce() {
        val alarms = listOf(
            alarm(nfcTagUid = "deadbeef", dismissWithoutTag = false),
            alarm(nfcTagUid = null, dismissWithoutTag = false),
            alarm(nfcTagUid = "a1b2c3d4", dismissWithoutTag = false),
            alarm(nfcTagUid = "deadbeef", dismissWithoutTag = false),
        )
        assertEquals(listOf("a1b2c3d4", "deadbeef"), registeredTagUids("a1b2c3d4", alarms))
        assertEquals(listOf("deadbeef", "a1b2c3d4"), registeredTagUids(null, alarms))
    }

    private fun alarm(nfcTagUid: String?, dismissWithoutTag: Boolean) = AlarmEntity(
        hour = 7,
        minute = 0,
        nfcTagUid = nfcTagUid,
        dismissWithoutTag = dismissWithoutTag,
    )
}
