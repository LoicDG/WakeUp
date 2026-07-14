package com.loic.wakeup.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.loic.wakeup.data.AppBlockStore
import com.loic.wakeup.domain.AppBlockPolicy
import com.loic.wakeup.domain.PowerMenuPolicy
import com.loic.wakeup.ui.screens.AlarmRingingActivity

/**
 * Enforces the app-blocking feature. Android delivers a [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED]
 * event every time a new window/app comes to the foreground; on each one we consult
 * [AppBlockPolicy]. If the foreground app should be blocked (feature on + an alarm is ringing +
 * the app isn't allow-listed) we bring the WakeUp ringing/lock screen back to the front.
 *
 * This also hardens the "can't escape the alarm" behaviour: whether the user opens another app,
 * presses HOME, or swipes to recents, the ringing screen is pulled back — far more reliable than
 * the activity's own onStop() relaunch fallback.
 *
 * Requires the user to enable the service in Android's Accessibility settings; the OS won't grant it
 * from code. Does nothing until then, so the app degrades gracefully.
 */
class AppBlockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Best-effort power-menu guard: if the global-actions dialog surfaces during an alarm,
        // send BACK to close it and pull the ringing screen back. Apps can't hide this dialog
        // without device owner, so this only *dismisses* it — a fast, deliberate tap could still
        // land first, and a hardware power-off still works (by design).
        if (PowerMenuPolicy.shouldDismiss(
                packageName = pkg,
                className = event.className?.toString(),
                alarmActive = AlarmService.isRunning,
                guardEnabled = AppBlockStore.powerMenuGuardEnabled.value,
            )
        ) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            bringBackLockScreen()
            return
        }

        val block = AppBlockPolicy.shouldBlock(
            foregroundPackage = pkg,
            selfPackage = packageName,
            allowedPackages = AppBlockStore.allowedPackages.value,
            alarmActive = AlarmService.isRunning,
            featureEnabled = AppBlockStore.enabled.value,
        )
        if (block) bringBackLockScreen()
    }

    override fun onInterrupt() { /* no-op */ }

    private fun bringBackLockScreen() {
        val intent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, AlarmService.runningAlarmId)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
