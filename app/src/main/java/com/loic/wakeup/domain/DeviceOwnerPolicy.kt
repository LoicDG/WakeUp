package com.loic.wakeup.domain

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.loic.wakeup.receiver.WakeUpDeviceAdminReceiver

/**
 * Thin, defensively-guarded wrapper around [DevicePolicyManager] for the optional kiosk
 * (lock-task) layer that suppresses the power menu while an alarm is ringing.
 *
 * WakeUp is only ever *device owner* if it was provisioned over ADB:
 *
 *   adb shell dpm set-device-owner com.loic.wakeup/.receiver.WakeUpDeviceAdminReceiver
 *
 * When it is NOT device owner — the normal case on any ordinary install — every call here
 * degrades to a logged no-op. The app then runs as an ordinary full-screen alarm and can
 * never lock the user out or crash. Everything below is feature-detected and reversible.
 */
object DeviceOwnerPolicy {
    private const val TAG = "DeviceOwnerPolicy"

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, WakeUpDeviceAdminReceiver::class.java)

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    /** True only when provisioned as device owner via ADB. Safe to call anytime. */
    fun isDeviceOwner(context: Context): Boolean = try {
        dpm(context).isDeviceOwnerApp(context.packageName)
    } catch (t: Throwable) {
        Log.w(TAG, "isDeviceOwner check failed", t)
        false
    }

    /**
     * Whitelist our package for lock task and configure lock-task features so the power menu
     * is suppressed while the alarm rings.
     *
     * `LOCK_TASK_FEATURE_GLOBAL_ACTIONS` is deliberately OMITTED from the feature set: with it
     * gone, long-pressing the power button inside lock task produces no power menu — which is
     * the entire point of this layer, so the user can't tap "Power off"/"Restart" to duck the
     * alarm. A hardware power-off (holding the button through the firmware path) still works and
     * is an intended, clean exit — we add no boot/shutdown re-arm to fight it.
     *
     * No-op (logged) when not device owner.
     */
    fun enableAlarmLockdown(context: Context) {
        if (!isDeviceOwner(context)) {
            Log.i(TAG, "Not device owner — skipping lockdown, running as a normal alarm")
            return
        }
        try {
            val dpm = dpm(context)
            val admin = adminComponent(context)
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
            // Lock-task feature flags require API 28. Below that, lock task still runs (screen
            // pinning already hides the power menu); we just can't tune the feature set.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Keep notifications + the system-info bar visible so it still feels like a
                // phone, but NOT GLOBAL_ACTIONS -> the power menu stays suppressed.
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS,
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "enableAlarmLockdown failed — continuing as a normal alarm", t)
        }
    }

    /**
     * Restore the platform-default lock-task features and clear the whitelist. Called on
     * dismiss so nothing device-owner-related lingers once the alarm is gone. No-op when not
     * device owner.
     */
    fun disableAlarmLockdown(context: Context) {
        if (!isDeviceOwner(context)) return
        try {
            val dpm = dpm(context)
            val admin = adminComponent(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Platform default is GLOBAL_ACTIONS enabled — restore it explicitly so the
                // suppression never outlives a ringing alarm.
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
            }
            dpm.setLockTaskPackages(admin, emptyArray())
        } catch (t: Throwable) {
            Log.w(TAG, "disableAlarmLockdown failed", t)
        }
    }

    /**
     * Escape hatch: fully relinquish device-owner status, returning the phone to a normal,
     * unmanaged state. Backs the hidden debug action. After this, re-provisioning requires ADB
     * again (`dpm set-device-owner`). Returns true if ownership was actually cleared. Safe
     * no-op when not device owner.
     */
    fun relinquishDeviceOwner(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        return try {
            @Suppress("DEPRECATION") // still the only API to self-clear device owner
            dpm(context).clearDeviceOwnerApp(context.packageName)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "clearDeviceOwnerApp failed", t)
            false
        }
    }
}
