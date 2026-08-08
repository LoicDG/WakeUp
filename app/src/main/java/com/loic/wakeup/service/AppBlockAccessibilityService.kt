package com.loic.wakeup.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
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

    private val handler = Handler(Looper.getMainLooper())

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
            dismissPowerMenu()
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

    /**
     * Close the power menu, then pull the ringing screen back over it.
     *
     * The BACK **must be delayed**: the window-state event arrives while the global-actions dialog
     * is still animating in and has not taken input focus yet, so a BACK sent right away is
     * delivered to the still-focused [AlarmRingingActivity] — which swallows BACK by design — and
     * the menu stays open. (`performGlobalAction` still returns true, so the failure is silent.)
     *
     * A single well-timed BACK isn't enough, for two reasons:
     *  - a user who out-races the guard taps *through* the menu ("Power off" → confirm), and BACK
     *    only pops one layer at a time, landing them back on the power menu instead of out of it;
     *  - the confirm layer may not emit a window event our markers catch, so we can't rely on
     *    being re-triggered for it.
     *
     * So we fire a dense burst instead: a first attempt as soon as the dialog can plausibly hold
     * focus, then one every [POWER_MENU_BACK_INTERVAL_MS] for [POWER_MENU_BACK_ATTEMPTS] tries.
     * Extra BACKs are harmless — the ringing activity ignores them — which makes the exact timing
     * of any single shot unimportant. Each tick bails out if the alarm is no longer ringing.
     *
     * The burst is deliberately *blind*: it does not check whether System UI still owns the
     * foreground before each BACK. `rootInActiveWindow` is always null here because the service
     * declares `canRetrieveWindowContent="false"` (res/xml), and we'd rather keep it that way —
     * reading every screen's contents is a far larger privilege than this guard needs.
     *
     * This stays best-effort: apps can't hide the global-actions dialog without device owner, and a
     * hardware power-off remains an intended escape.
     */
    private fun dismissPowerMenu() {
        // Every power-menu window event schedules a burst; drop any in-flight one so repeated
        // events can't stack unbounded overlapping bursts.
        handler.removeCallbacksAndMessages(POWER_MENU_TOKEN)
        for (attempt in 0 until POWER_MENU_BACK_ATTEMPTS) {
            val delay = POWER_MENU_BACK_DELAY_MS + attempt * POWER_MENU_BACK_INTERVAL_MS
            handler.postAtTime(
                {
                    if (!AlarmService.isRunning) return@postAtTime
                    performGlobalAction(GLOBAL_ACTION_BACK)
                },
                POWER_MENU_TOKEN,
                android.os.SystemClock.uptimeMillis() + delay,
            )
        }
        val settleDelay =
            POWER_MENU_BACK_DELAY_MS + POWER_MENU_BACK_ATTEMPTS * POWER_MENU_BACK_INTERVAL_MS + 200L
        handler.postAtTime(
            { if (AlarmService.isRunning) bringBackLockScreen() },
            POWER_MENU_TOKEN,
            android.os.SystemClock.uptimeMillis() + settleDelay,
        )
    }

    private fun bringBackLockScreen() {
        val intent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, AlarmService.runningAlarmId)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private companion object {
        /** Token so an incoming burst can cancel the previous one wholesale. */
        val POWER_MENU_TOKEN = Any()

        /**
         * First attempt: the earliest the dialog plausibly holds input focus. Kept short so a fast
         * tap has less room to get through; if it lands too early it's simply wasted and the next
         * tick covers it.
         */
        const val POWER_MENU_BACK_DELAY_MS = 80L

        /** Gap between attempts — tight enough to interrupt a deliberate two-tap power-off. */
        const val POWER_MENU_BACK_INTERVAL_MS = 80L

        /** ~1.5 s of coverage in total. */
        const val POWER_MENU_BACK_ATTEMPTS = 18
    }
}
