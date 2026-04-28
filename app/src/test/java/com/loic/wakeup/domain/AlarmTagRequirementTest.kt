package com.loic.wakeup.domain

import com.loic.wakeup.data.AlarmEntity
import org.junit.Assert.assertFalse
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

    private fun alarm(nfcTagUid: String?, dismissWithoutTag: Boolean) = AlarmEntity(
        hour = 7,
        minute = 0,
        nfcTagUid = nfcTagUid,
        dismissWithoutTag = dismissWithoutTag,
    )
}
