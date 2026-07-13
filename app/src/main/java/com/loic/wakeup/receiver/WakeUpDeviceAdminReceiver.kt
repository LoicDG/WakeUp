package com.loic.wakeup.receiver

import android.app.admin.DeviceAdminReceiver

/**
 * Device-admin receiver required so WakeUp can be provisioned as *device owner* over ADB:
 *
 *   adb shell dpm set-device-owner com.loic.wakeup/.receiver.WakeUpDeviceAdminReceiver
 *
 * Being device owner is what unlocks the kiosk (lock-task) layer used to suppress the power
 * menu while an alarm rings (see [com.loic.wakeup.domain.DeviceOwnerPolicy]). We need no
 * admin callbacks of our own — the base implementation is enough — so this is deliberately
 * empty. All behaviour is opt-in and reversible; if the app is never provisioned this class
 * simply sits inert and the app runs as a normal alarm.
 */
class WakeUpDeviceAdminReceiver : DeviceAdminReceiver()
