package com.loic.wakeup.domain

/**
 * Pure decision logic for the best-effort power-menu guard.
 *
 * Apps cannot *hide* the system power menu (global-actions dialog) without device-owner
 * privileges, but an accessibility service can notice it appear and immediately dismiss it.
 * This is what [com.loic.wakeup.service.AppBlockAccessibilityService] does while an alarm is
 * ringing, so a groggy tap on "Power off" / "Restart" can't quietly kill the alarm. A real
 * hardware power-off still works and stays an intended escape.
 *
 * Detection is a heuristic on the window that just came to the foreground: the global-actions
 * dialog is hosted by System UI and its window class name contains a recognisable marker. The
 * exact class varies by Android version / OEM (One UI included), so we match a small set of
 * substrings rather than one exact name. Because the whole check is gated on an active alarm,
 * a rare false positive only ever means dismissing some System UI dialog *during* an alarm —
 * acceptable, since escaping the alarm through it is exactly what we're preventing.
 */
object PowerMenuPolicy {

    /** System UI hosts the global-actions dialog on AOSP and every OEM skin we care about. */
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    /**
     * Lower-cased markers seen in the global-actions / power-menu window class name across
     * AOSP and One UI (e.g. `GlobalActionsDialog`, `GlobalActionsDialogLite`, Samsung's
     * power/shutdown variants). Kept narrow so ordinary System UI windows (keyguard, status
     * bar, volume) don't match.
     */
    private val POWER_MENU_MARKERS = listOf(
        "globalaction",
        "powermenu",
        "poweraction",
        "shutdown",
    )

    /** True when the given foreground window looks like the system power menu. */
    fun isPowerMenu(packageName: String?, className: String?): Boolean {
        if (packageName != SYSTEM_UI_PACKAGE) return false
        val cls = className?.lowercase() ?: return false
        return POWER_MENU_MARKERS.any { cls.contains(it) }
    }

    /**
     * Whether the accessibility service should dismiss the window that just appeared. Only
     * fires while an alarm is ringing and the guard is enabled — otherwise the power menu is
     * left completely alone.
     */
    fun shouldDismiss(
        packageName: String?,
        className: String?,
        alarmActive: Boolean,
        guardEnabled: Boolean,
    ): Boolean {
        if (!guardEnabled) return false
        if (!alarmActive) return false
        return isPowerMenu(packageName, className)
    }
}
