package com.loic.wakeup.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBlockPolicyTest {

    private val self = "com.loic.wakeup"
    private val allowed = setOf("com.android.dialer", "com.android.settings")

    @Test
    fun blocksArbitraryApp_whenEnabledAndAlarmActive() {
        assertTrue(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = "com.instagram.android",
                selfPackage = self,
                allowedPackages = allowed,
                alarmActive = true,
                featureEnabled = true,
            )
        )
    }

    @Test
    fun doesNotBlock_whenFeatureDisabled() {
        assertFalse(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = "com.instagram.android",
                selfPackage = self,
                allowedPackages = allowed,
                alarmActive = true,
                featureEnabled = false,
            )
        )
    }

    @Test
    fun doesNotBlock_whenNoAlarmActive() {
        assertFalse(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = "com.instagram.android",
                selfPackage = self,
                allowedPackages = allowed,
                alarmActive = false,
                featureEnabled = true,
            )
        )
    }

    @Test
    fun neverBlocksSelf() {
        assertFalse(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = self,
                selfPackage = self,
                allowedPackages = emptySet(),
                alarmActive = true,
                featureEnabled = true,
            )
        )
    }

    @Test
    fun neverBlocksAllowlistedApp() {
        assertFalse(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = "com.android.dialer",
                selfPackage = self,
                allowedPackages = allowed,
                alarmActive = true,
                featureEnabled = true,
            )
        )
    }

    @Test
    fun neverBlocksCoreSystemPackages() {
        // System UI / framework windows overlay everything and must not trigger a relaunch loop.
        assertFalse(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = "com.android.systemui",
                selfPackage = self,
                allowedPackages = emptySet(),
                alarmActive = true,
                featureEnabled = true,
            )
        )
        assertFalse(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = "android",
                selfPackage = self,
                allowedPackages = emptySet(),
                alarmActive = true,
                featureEnabled = true,
            )
        )
    }

    @Test
    fun blocksLauncher_soHomeCannotEscapeTheAlarm() {
        // The home launcher is a normal package; pressing HOME must bounce back to the alarm.
        assertTrue(
            AppBlockPolicy.shouldBlock(
                foregroundPackage = "com.sec.android.app.launcher",
                selfPackage = self,
                allowedPackages = allowed,
                alarmActive = true,
                featureEnabled = true,
            )
        )
    }
}
