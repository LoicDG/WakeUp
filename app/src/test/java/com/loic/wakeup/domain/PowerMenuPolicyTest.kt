package com.loic.wakeup.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerMenuPolicyTest {

    private val systemUi = "com.android.systemui"

    @Test
    fun detectsAospGlobalActionsDialog() {
        assertTrue(
            PowerMenuPolicy.isPowerMenu(
                systemUi,
                "com.android.systemui.globalactions.GlobalActionsDialog",
            )
        )
        assertTrue(
            PowerMenuPolicy.isPowerMenu(
                systemUi,
                "com.android.systemui.globalactions.GlobalActionsDialogLite",
            )
        )
    }

    @Test
    fun detectsOemPowerMenuVariants() {
        assertTrue(PowerMenuPolicy.isPowerMenu(systemUi, "com.samsung.PowerMenuDialog"))
        assertTrue(PowerMenuPolicy.isPowerMenu(systemUi, "some.ShutdownDialog"))
    }

    /** The exact class One UI reports on the test device (Galaxy S24, Android 16). */
    @Test
    fun detectsOneUiGlobalActionsDialog() {
        assertTrue(
            PowerMenuPolicy.isPowerMenu(
                systemUi,
                "com.samsung.android.globalactions.presentation.view." +
                    "SamsungGlobalActionsDialogBase\$ActionsDialog",
            )
        )
    }

    @Test
    fun ignoresOtherSystemUiWindows() {
        // Keyguard, status bar, volume, notification shade must not be treated as the power menu.
        assertFalse(PowerMenuPolicy.isPowerMenu(systemUi, "com.android.systemui.statusbar.phone.StatusBar"))
        assertFalse(PowerMenuPolicy.isPowerMenu(systemUi, "com.android.systemui.volume.VolumeDialogImpl"))
        assertFalse(PowerMenuPolicy.isPowerMenu(systemUi, "com.android.keyguard.KeyguardHostView"))
    }

    @Test
    fun ignoresNonSystemUiPackages() {
        // Only System UI hosts the global-actions dialog; a same-named class elsewhere is not it.
        assertFalse(PowerMenuPolicy.isPowerMenu("com.evil.app", "some.GlobalActionsDialog"))
    }

    @Test
    fun ignoresNullClassName() {
        assertFalse(PowerMenuPolicy.isPowerMenu(systemUi, null))
    }

    @Test
    fun dismissesPowerMenu_whenGuardOnAndAlarmActive() {
        assertTrue(
            PowerMenuPolicy.shouldDismiss(
                packageName = systemUi,
                className = "com.android.systemui.globalactions.GlobalActionsDialog",
                alarmActive = true,
                guardEnabled = true,
            )
        )
    }

    @Test
    fun doesNotDismiss_whenGuardDisabled() {
        assertFalse(
            PowerMenuPolicy.shouldDismiss(
                packageName = systemUi,
                className = "com.android.systemui.globalactions.GlobalActionsDialog",
                alarmActive = true,
                guardEnabled = false,
            )
        )
    }

    @Test
    fun doesNotDismiss_whenNoAlarmActive() {
        assertFalse(
            PowerMenuPolicy.shouldDismiss(
                packageName = systemUi,
                className = "com.android.systemui.globalactions.GlobalActionsDialog",
                alarmActive = false,
                guardEnabled = true,
            )
        )
    }

    @Test
    fun doesNotDismiss_ordinaryAppDuringAlarm() {
        assertFalse(
            PowerMenuPolicy.shouldDismiss(
                packageName = "com.instagram.android",
                className = "com.instagram.android.MainTabActivity",
                alarmActive = true,
                guardEnabled = true,
            )
        )
    }
}
