# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Read Summary.md for a comprehensive understanding of each file

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.loic.wakeup.SomeTest"

# Lint
./gradlew lint
```

## Architecture

Single-module Android app (`app/`) using MVVM + Repository pattern with Jetpack Compose UI.

**Layer overview:**

- `data/` — Room database (`AlarmDatabase`, `AlarmDao`, `AlarmEntity`) + `AlarmRepository` (thin DAO wrapper) + `NfcTagStore` (encrypted SharedPreferences via `androidx.security.crypto` for the paired NFC tag UID)
- `domain/` — `AlarmScheduler` (wraps `AlarmManager.setAlarmClock`) + `NextTriggerCalculator` (pure logic for next trigger epoch millis)
- `service/` — `AlarmService`: foreground service that plays ringtone, vibrates, shows notification with snooze action; handles snooze rescheduling
- `receiver/` — `AlarmReceiver` (fired by AlarmManager, starts `AlarmService`) + `BootReceiver` (reschedules all enabled alarms after reboot)
- `ui/screens/` — Compose screens (`AlarmListScreen`, `AlarmEditScreen`, `NfcSettingsScreen`) + `AlarmRingingActivity` (lock-screen overlay, handles NFC foreground dispatch for tag-based dismiss)
- `ui/viewmodel/` — one ViewModel per screen
- `ui/nav/NavGraph.kt` — Navigation Compose graph

**Key domain rules:**

- `AlarmEntity.daysMask` is a bitmask where bit 0 = Monday … bit 6 = Sunday; `0` means one-shot alarm
- `NextTriggerCalculator` maps `Calendar.DAY_OF_WEEK` (Sun=1…Sat=7) to these bit indices
- NFC dismiss: `AlarmRingingActivity` enables foreground NFC dispatch; scanning the registered tag (hex UID stored in `NfcTagStore`) broadcasts `ACTION_DISMISS` to stop `AlarmService`
- Snooze increments `snoozeCount` in DB and calls `AlarmScheduler.scheduleAt` for `snoozeDurationSeconds` in the future; dismissed/expired one-shot alarms are not rescheduled

**DI:** No DI framework — dependencies are constructed manually (e.g., `AlarmDatabase.getInstance(context).alarmDao()` directly in `AlarmService`).

## SDK & Build Config

- `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`, Java 17
- Package: `com.loic.wakeup`
- KSP (not kapt) for Room annotation processing
- No Hilt/Koin — manual construction everywhere

## Permissions (AndroidManifest)

NFC (required hardware), `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `VIBRATE`.

The `AlarmService` foreground service type is `specialUse` (subtype: `alarm`).

## AlarmEntity fields

| Field | Type | Notes |
|---|---|---|
| `id` | Int (autoGenerate) | PK |
| `hour` / `minute` | Int | 24-h time |
| `daysMask` | Int | bitmask Mon=bit0…Sun=bit6; 0 = one-shot |
| `label` | String | optional display name |
| `ringtoneUri` | String | empty = system default alarm |
| `enabled` | Boolean | false after one-shot fires or manual disable |
| `snoozeCount` | Int | reset to 0 on fresh ring |
| `maxSnoozes` | Int | default 3 |
| `snoozeDurationSeconds` | Int | default 20 |

## Key invariants / rules

- **NFC tag required before activating any alarm.** Both `AlarmListViewModel.setEnabled` and `AlarmEditViewModel.save` check `NfcTagStore.getUid() != null` and emit an error event if null.
- **One-shot alarms** (`daysMask == 0`): `AlarmReceiver` calls `repo.setEnabled(id, false)` after firing; they are never re-scheduled.
- **Repeating alarms** (`daysMask != 0`): `AlarmReceiver` calls `AlarmScheduler.schedule(alarm)` to queue the next occurrence.
- **Snooze flow**: `AlarmService.handleSnooze()` increments DB `snoozeCount`, reschedules internally via `delay` (service stays alive), does NOT call `scheduleAt`. The `AlarmScheduler.scheduleAt(isSnooze=true)` path exists but is currently unused by the snooze flow — snooze is handled entirely inside the running service.
- **Volume keys blocked** in `AlarmRingingActivity.dispatchKeyEvent`; back button is a no-op; HOME-away is countered by re-launching the activity after 600 ms in `onPause`.
- `AlarmService.isRunning` / `runningAlarmId` are `@Volatile` companion-object flags the ringing UI reads to detect if the service is still alive.
- `AlarmService.ringState` is a `StateFlow<RingState>` (companion object); `AlarmRingingActivity` collects it directly without a ViewModel.

## Theme

Dark-only (`WakeUpTheme` always uses `darkColorScheme`; `dynamicColor = false`). Named colors: `Amber` (primary/accent), `Midnight` (background), `DeepNavy` (surface), `StarWhite` (on-surface), `SlateBlue` (variant), `MorningBlue` (secondary), `NavyVariant`, `NavyOutline`. Display clock uses `FontFamily.Serif` at 80 sp.

## Navigation routes

- `alarm_list` — `AlarmListScreen`
- `alarm_edit/{alarmId}` — `AlarmEditScreen`; `alarmId = -1` means new alarm
- `nfc_settings` — `NfcSettingsScreen`

`AlarmRingingActivity` is a separate `Activity` (not part of the Compose nav graph); launched directly from `AlarmReceiver` and via full-screen `PendingIntent` in the notification.

## NFC tag storage

`NfcTagStore` wraps `EncryptedSharedPreferences` (AES256-GCM). Tag UID is stored as a lowercase hex string (e.g. `"a1b2c3d4"`). Comparison in `AlarmRingingActivity.onNewIntent` is `scannedHex == storedUid` (exact string equality, lowercase).

## Notification channel

`WakeUpApp.ALARM_CHANNEL_ID = "wakeup_alarms"`, created in `Application.onCreate`. Channel has `bypassDnd = true`, vibration enabled, `VISIBILITY_PUBLIC`.

## AlarmRingingActivity — Lock Screen Pitfalls
  - NFC reader mode is enabled in  and disabled in .
  - Any call that causes the activity to lose focus (keyguard prompts,          
  permission dialogs)                                                           
    will disable NFC. Never invoke  or similar from this
   activity.                                                                    
  - The activity must retain focus over the lock screen to keep NFC active. 
