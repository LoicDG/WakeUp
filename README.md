# WakeUp

An Android alarm app that requires you to physically scan an NFC tag to dismiss the alarm — no more sleep-tapping snooze from bed.

## Features

- **NFC-enforced dismissal** — scan a registered NFC tag to stop the alarm; there is no tap-to-dismiss button
- **Repeating or one-shot alarms** — set alarms for specific days of the week, or a one-time alarm that disables itself after firing
- **Snooze** — configurable snooze duration and maximum snooze count per alarm
- **Lock-screen overlay** — alarm fires over the lock screen; volume keys and back button are blocked
- **Custom ringtone** — pick any ringtone from the system or leave blank for the default alarm sound
- **Boot persistence** — all enabled alarms are rescheduled automatically after device reboot
- **Dark theme** with an amber/midnight color palette

## Requirements

- Android 8.0 (API 26) or higher
- A device with NFC hardware (declared as required in the manifest)
- One physical NFC tag (any standard NFC tag works)

## How It Works

1. **Register your NFC tag** — go to NFC Settings and scan your tag once to register it. All alarms are locked behind this step; you cannot enable or save an alarm until a tag is registered.
2. **Create an alarm** — set the time, choose repeat days (or leave unset for one-shot), add a label, pick a ringtone, and configure snooze.
3. **When the alarm fires** — the app displays a full-screen ringing UI over the lock screen. To dismiss it, scan the registered NFC tag. A snooze button is also available (up to the configured maximum).

## Project Structure

```
app/src/main/java/com/loic/wakeup/
├── data/               # Room database, DAO, AlarmEntity, NfcTagStore
├── domain/             # AlarmScheduler, NextTriggerCalculator
├── receiver/           # AlarmReceiver (AlarmManager), BootReceiver
├── service/            # AlarmService (foreground, ringtone, vibration)
├── ui/
│   ├── nav/            # Compose navigation graph
│   ├── screens/        # AlarmListScreen, AlarmEditScreen, NfcSettingsScreen,
│   │                   #   AlarmRingingActivity
│   ├── theme/          # WakeUpTheme (dark-only, Amber + Midnight palette)
│   └── viewmodel/      # AlarmListViewModel, AlarmEditViewModel, NfcSettingsViewModel
├── MainActivity.kt
└── WakeUpApp.kt
```

Architecture: **MVVM + Repository** with Jetpack Compose UI and Room for persistence. No DI framework — dependencies are constructed manually.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on a connected device
./gradlew installDebug

# Run all unit tests
./gradlew test

# Lint
./gradlew lint
```

**SDK:** `minSdk = 26`, `targetSdk = 34`, Java 17, KSP for Room annotation processing.

## Permissions

| Permission | Purpose |
|---|---|
| `NFC` | Scan tag to dismiss alarm |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Fire alarms at the exact scheduled time |
| `USE_FULL_SCREEN_INTENT` | Show ringing UI over the lock screen |
| `POST_NOTIFICATIONS` | Alarm notification with snooze action |
| `WAKE_LOCK` | Keep CPU awake while alarm is ringing |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Run alarm service in foreground |
| `VIBRATE` | Vibrate on alarm |

## Kiosk Lockdown (optional, device owner)

An **optional** layer that suppresses the power menu while an alarm is ringing, so it can't be
tapped away with "Power off" / "Restart". It uses Android lock task (screen pinning) with the
`GLOBAL_ACTIONS` feature deliberately turned off, and is only active when WakeUp is provisioned as
**device owner** over ADB. This is entirely feature-detected and reversible:

- If WakeUp is **not** device owner (the default on any normal install), every device-owner call is
  a logged no-op and the alarm runs as an ordinary full-screen alarm — it can never lock you out or
  crash.
- Only lock task + power-menu suppression are used. No `DISALLOW_*` restrictions and no
  boot/shutdown re-arm — a **hardware power-off stays a clean exit** by design.
- The only in-app exit from the kiosk is a successful NFC dismiss, which leaves lock task and
  restores the power menu.

### Provisioning (once, over ADB — requires no root)

The device must have no other accounts on the primary user (a fresh device or one with accounts
removed), which is Android's requirement for `set-device-owner`:

```bash
adb shell dpm set-device-owner com.loic.wakeup/.receiver.WakeUpDeviceAdminReceiver
```

### Removal

```bash
adb shell dpm remove-active-admin com.loic.wakeup/.receiver.WakeUpDeviceAdminReceiver
```

### Escape hatch (no ADB needed)

If you ever need to relinquish device-owner status from the phone itself, open **Settings →
App blocking** and **long-press the "KIOSK LOCKDOWN" card**. That calls `clearDeviceOwnerApp(...)`
and drops any active lock task, returning the phone to a normal, unmanaged state.

## NFC Tag Details

- Any single NFC tag can be registered; the UID is stored in `EncryptedSharedPreferences` (AES256-GCM).
- Removing the registered tag disables all alarms automatically.
- Replacing the tag requires visiting NFC Settings and scanning the new tag.
- Tag comparison is exact lowercase hex string equality.
