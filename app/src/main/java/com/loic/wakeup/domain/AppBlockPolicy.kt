package com.loic.wakeup.domain

/**
 * Pure decision logic for the app-blocking feature: given the app currently in the
 * foreground and the current state, decide whether it should be kicked away in favour
 * of the WakeUp lock screen.
 *
 * The feature is deliberately scoped to *only while an alarm is ringing* — blocking is
 * coextensive with an active [com.loic.wakeup.service.AlarmService]. The moment the alarm
 * is dismissed (by scanning the tag) the service stops, [alarmActive] becomes false, and
 * every app is free again.
 */
object AppBlockPolicy {

    /**
     * Core framework packages that render overlays (system UI, dialogs, the framework
     * itself) on top of any app. Acting on these would fight transient system windows and
     * risk a relaunch loop, so they are never blocked. Note this does NOT include the home
     * launcher — pressing HOME must bounce back to the alarm, so the launcher stays blockable.
     */
    private val CORE_SYSTEM_PACKAGES = setOf("android", "com.android.systemui")

    fun shouldBlock(
        foregroundPackage: String,
        selfPackage: String,
        allowedPackages: Set<String>,
        alarmActive: Boolean,
        featureEnabled: Boolean,
    ): Boolean {
        if (!featureEnabled) return false
        if (!alarmActive) return false
        if (foregroundPackage == selfPackage) return false
        if (foregroundPackage in CORE_SYSTEM_PACKAGES) return false
        if (foregroundPackage in allowedPackages) return false
        return true
    }
}
