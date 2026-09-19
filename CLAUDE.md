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

- `data/` — Room database (`AlarmDatabase`, `AlarmDao`, `AlarmEntity`, `TagFailsafeDao`, `TagFailsafeEntity`) + `AlarmRepository` / `TagFailsafeRepository` (thin DAO wrappers) + `NfcTagStore` (encrypted SharedPreferences via `androidx.security.crypto` for the paired NFC tag UID)
- `domain/` — `AlarmScheduler` (wraps `AlarmManager.setAlarmClock`; also queues failsafe checks) + `NextTriggerCalculator` (pure logic for next trigger epoch millis) + the failsafe pieces: `FailsafePolicy` / `GeoPoint` (pure), `CurrentLocation` / `LocationAccess` (framework `LocationManager`), `AlarmDeactivator` (shared skip/turn-off logic)
- `service/` — `AlarmService`: foreground service that plays ringtone, vibrates, shows notification with snooze action; handles snooze rescheduling. `FailsafeService`: short location-type foreground service running one failsafe check
- `receiver/` — `AlarmReceiver` (fired by AlarmManager, starts `AlarmService`) + `FailsafeReceiver` (daily failsafe check time, starts `FailsafeService`) + `BootReceiver` (reschedules all enabled alarms and failsafes after reboot)
- `ui/screens/` — Compose screens (`AlarmListScreen`, `AlarmEditScreen`, `NfcSettingsScreen`, `AppBlockSettingsScreen`, `FailsafeSettingsScreen`) + `AlarmRingingActivity` (lock-screen overlay, handles NFC foreground dispatch for tag-based dismiss)
- `ui/viewmodel/` — one ViewModel per screen
- `ui/nav/NavGraph.kt` — Navigation Compose graph

**Key domain rules:**

- `AlarmEntity.daysMask` is a bitmask where bit 0 = Sunday … bit 6 = Saturday; `0` means one-shot alarm
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

NFC (required hardware), `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `VIBRATE`, `QUERY_ALL_PACKAGES` (to list installed apps for the app-blocking allow-list — fine for a sideloaded app, but would need a Play Store declaration if ever published).

The app-blocking feature also declares an `AccessibilityService` (`AppBlockAccessibilityService`) bound with `BIND_ACCESSIBILITY_SERVICE`; the OS only activates it after the user turns it on in **Settings → Accessibility**. It cannot be granted from code.

The `AlarmService` foreground service type is `specialUse` (subtype: `alarm`).

The location failsafe adds `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION` (precise is required — approximate is ~2 km), `ACCESS_BACKGROUND_LOCATION` ("Allow all the time", since checks run from an alarm while the app is in the background; on Android 11+ it can only be granted from the system settings page the permission request opens) and `FOREGROUND_SERVICE_LOCATION`. `FailsafeService`'s foreground service type is `location`.

## AlarmEntity fields

| Field | Type | Notes |
|---|---|---|
| `id` | Int (autoGenerate) | PK |
| `hour` / `minute` | Int | 24-h time |
| `daysMask` | Int | bitmask Sun=bit0…Sat=bit6; 0 = one-shot |
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
- **Snooze flow**: `AlarmService.handleSnooze()` uses `SnoozeCalculator.decide()` to either increment DB `snoozeCount` and schedule a real re-ring via `AlarmScheduler.scheduleAt(isSnooze=true)`, then `stopSelf()` — or do nothing once `snoozeCount >= maxSnoozes`. The re-ring comes back through `AlarmReceiver` (which wakes the device from deep sleep), so snooze survives the screen turning off or the service being reclaimed. The ringing activity calls `finish()` on snooze. Snooze re-rings use a dedicated request-code offset (`SNOOZE_REQUEST_CODE_OFFSET`) so they never collide with a repeating alarm's next occurrence; `ACTION_DISMISS` calls `AlarmScheduler.cancelSnooze()` to drop a pending re-ring. On a snooze ring, `AlarmReceiver`/`AlarmService` must NOT reset `snoozeCount` (gated on the `isSnooze` extra), otherwise max-snooze counting breaks.
- **Volume keys blocked** in `AlarmRingingActivity.dispatchKeyEvent`; back button is a no-op; HOME-away is countered by re-launching the activity after 600 ms in `onPause`.
- `AlarmService.isRunning` / `runningAlarmId` are `@Volatile` companion-object flags the ringing UI reads to detect if the service is still alive.
- `AlarmService.ringState` is a `StateFlow<RingState>` (companion object); `AlarmRingingActivity` collects it directly without a ViewModel.

## App blocking (during alarms)

- Goal: while an alarm is ringing, block every app except an allow-list, forcing the user to scan the tag (which dismisses the alarm) to regain their phone. Scoped **only to an active alarm** — blocking is coextensive with a running `AlarmService`.
- **Mechanism:** `AppBlockAccessibilityService` receives `TYPE_WINDOW_STATE_CHANGED` on every foreground-app change and consults the pure `AppBlockPolicy.shouldBlock(...)`. When it returns true it relaunches `AlarmRingingActivity` (`FLAG_ACTIVITY_REORDER_TO_FRONT or NEW_TASK`, with `runningAlarmId`), pulling the lock screen back over the blocked app. This also *hardens the anti-escape behaviour* — HOME/recents/other-app all bounce back to the ringing screen, far more robustly than the activity's own `onStop()` 800 ms relaunch (which stays as a fallback for when the service isn't enabled).
- **Gating:** `shouldBlock` requires `featureEnabled` (`AppBlockStore.enabled`) **and** `alarmActive` (`AlarmService.isRunning`). Never blocks WakeUp itself, `android`/`com.android.systemui`, or allow-listed packages. The home launcher is intentionally *not* auto-allowed, so pressing HOME during an alarm returns to the scan screen.
- **State:** `AppBlockStore` (plain `SharedPreferences`, `StateFlow`s) holds the master enable flag + the allow-listed package names, seeded once with the default dialer/SMS/Settings packages via `seedDefaultsIfNeeded`. Read directly by the accessibility service (same process).
- The service does nothing until the user enables it in Accessibility settings; `AppBlockSettingsScreen` shows live on/off status (re-checked on `ON_RESUME` via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`) and deep-links there.

## Location failsafe (per NFC tag)

- Goal: an alarm can only be dismissed with its tag, so an alarm ringing while you're away from the tag (travelling, sleeping elsewhere) can't be stopped. Each registered tag can have a failsafe: a spot (lat/lng) and a daily check time. At the check, if the device is more than `FailsafePolicy.RADIUS_METERS` (100 m) from the spot, the tag's alarms are deactivated.
- **Tags:** "registered" = the global tag (`NfcTagStore`) + every alarm's custom `nfcTagUid` (`registeredTagUids`). An alarm belongs to the tag `effectiveTagUid(globalUid)` returns (custom tag, else global; none if `dismissWithoutTag`) — the same lookup `AlarmRingingActivity` uses.
- **State:** `TagFailsafeEntity` (Room table `tag_failsafes`, PK = tag UID; `enabled`, `hour`/`minute`, nullable `latitude`/`longitude`). A failsafe without a spot can't be enabled and is never scheduled.
- **Scheduling:** `AlarmScheduler.scheduleFailsafe` uses `setExactAndAllowWhileIdle` (never shows as the "next alarm"); PendingIntent identity is the tag UID in the data URI (`wakeup-failsafe:<uid>`), not a request-code offset. Rescheduled on every edit, on boot, and by `FailsafeReceiver` itself each day (before starting the check, so a failed check never breaks the chain).
- **Check flow:** `FailsafeReceiver` drops the failsafe if its tag is no longer registered (lazy orphan cleanup), queues tomorrow, and starts `FailsafeService` only if some alarm would actually be deactivated. The service goes foreground (type `location`), gets a fix (`CurrentLocation`: all enabled providers in parallel, early exit on a ≤30 m fix, 30 s timeout, falls back to a ≤10 min old last-known fix), and deactivates via `AlarmDeactivator`. Missing background permission or no fix → alarms stay on and a notification says so.
- **Which alarms (`FailsafePolicy.alarmsToDeactivate`):** enabled alarms of that tag whose next occurrence is **before the next check** — later ones are left to that check (so a Friday-night check away never silences Monday; Sunday night's check decides). The currently ringing/snoozed alarm is excluded (deactivating it would cancel its snooze re-ring).
- **"Away" (`FailsafePolicy.isAway`):** `distance − fixAccuracy > 100 m` — only when the whole accuracy circle is outside. Borderline fixes keep the alarm armed.
- **"Deactivate" (`AlarmDeactivator`, shared with the reminder's action):** recurring → skip only the next occurrence (temporary disable + reboot-safe re-enable); one-shot → turned off.
- Replacing the global tag copies its failsafe to the new UID (`NfcSettingsViewModel.carryOverFailsafe`).

## Theme

Dark-only (`WakeUpTheme` always uses `darkColorScheme`; `dynamicColor = false`). Named colors: `Amber` (primary/accent), `Midnight` (background), `DeepNavy` (surface), `StarWhite` (on-surface), `SlateBlue` (variant), `MorningBlue` (secondary), `NavyVariant`, `NavyOutline`. Display clock uses `FontFamily.Serif` at 80 sp.

## Navigation routes

- `alarm_list` — `AlarmListScreen`
- `alarm_edit/{alarmId}` — `AlarmEditScreen`; `alarmId = -1` means new alarm
- `nfc_settings` — `NfcSettingsScreen` (the app's settings page; has buttons into `failsafe_settings` and `app_block_settings`)
- `app_block_settings` — `AppBlockSettingsScreen`
- `failsafe_settings` — `FailsafeSettingsScreen` (one card per registered tag + location-access status)

`AlarmRingingActivity` is a separate `Activity` (not part of the Compose nav graph); launched directly from `AlarmReceiver` and via full-screen `PendingIntent` in the notification.

## NFC tag storage

`NfcTagStore` wraps `EncryptedSharedPreferences` (AES256-GCM). Tag UID is stored as a lowercase hex string (e.g. `"a1b2c3d4"`). Comparison in `AlarmRingingActivity.onNewIntent` is `scannedHex == storedUid` (exact string equality, lowercase).

## Notification channel

`WakeUpApp.ALARM_CHANNEL_ID = "wakeup_alarms"`, created in `Application.onCreate`. Channel has `bypassDnd = true`, vibration enabled, `VISIBILITY_PUBLIC`. Also `REMINDER_CHANNEL_ID = "wakeup_reminders"` and `FAILSAFE_CHANNEL_ID = "wakeup_failsafe"` (silent "checking" FGS notification + one outcome notification per tag, posted with the tag UID as notification tag).

## AlarmRingingActivity — Lock Screen Pitfalls
  - NFC reader mode is enabled in  and disabled in .
  - Any call that causes the activity to lose focus (keyguard prompts,          
  permission dialogs)                                                           
    will disable NFC. Never invoke  or similar from this
   activity.                                                                    
  - The activity must retain focus over the lock screen to keep NFC active.

## Test device

Galaxy S24, Android 16 (One UI 7/8). Samsung gates NFC while the keyguard is active; the ringing UI shows over the lock screen but the NFC radio does not scan until the device is unlocked. The per-app override lives at `Settings → Connections → NFC and contactless payments → Read and write/P2P`-adjacent toggle ("Require unlock" or similar wording varies by One UI version). No app-side code change can bypass this — if the toggle is unavailable, lock-screen NFC dismiss is not possible on this device.
