# WakeUp Project Summary

## Overview
WakeUp is a single-module Android alarm app built with Jetpack Compose, Room, and NFC-based alarm dismissal. It schedules exact alarms, shows a lock-screen ringing UI, supports snooze, and requires registering a single NFC tag to dismiss a ringing alarm.

---

## Root files

- `build.gradle.kts`
  - Top-level Gradle plugin configuration. Applies aliases for Android application, Kotlin Android, Compose, and KSP plugins.
- `settings.gradle.kts`
  - Defines the root project name `WakeUp` and includes the `:app` module.
- `gradle.properties`
  - Gradle properties and build settings.
- `local.properties`
  - Local environment configuration, typically Android SDK path. Not part of app logic.
- `gradle/libs.versions.toml`
  - Central dependency and plugin version catalog used by the build scripts.
- `gradlew` / `gradle/wrapper`
  - Gradle wrapper scripts and configuration for reproducible builds.
- `CLAUDE.md`
  - Project guidance file describing architecture, NFC rules, and app invariants.

---

## App module: build & manifest

- `app/build.gradle.kts`
  - Configures the Android app module, compile SDK 34, min SDK 26, Compose support, Room KSP setup, and dependencies.
- `app/src/main/AndroidManifest.xml`
  - Declares required permissions, NFC hardware requirement, app theme, `MainActivity`, `AlarmRingingActivity`, `AlarmReceiver`, `BootReceiver`, and `AlarmService`.

---

## Application entry points

- `app/src/main/java/com/loic/wakeup/MainActivity.kt`
  - Hosts the Compose navigation graph, requests notification and full-screen intent permissions, and resumes the alarm ringing UI if an alarm service is already active.
- `app/src/main/java/com/loic/wakeup/WakeUpApp.kt`
  - Custom `Application` class that creates the alarm notification channel at startup.

---

## Data layer (`data` package)

- `AlarmDatabase.kt`
  - Room database definition and singleton instance provider.
- `AlarmDao.kt`
  - Data access object for alarm CRUD, enabled/disabled state, snooze counts, and query operations.
- `AlarmEntity.kt`
  - Room entity representing alarm settings: hour, minute, repeating days mask, label, ringtone URI, enabled state, snooze configuration, and tag-related invariants.
- `AlarmRepository.kt`
  - Thin repository wrapper around `AlarmDao` used by ViewModels and services.
- `NfcTagStore.kt`
  - Securely stores and retrieves the registered NFC tag UID using `EncryptedSharedPreferences`.
- `AppBlockStore.kt`
  - Plain-`SharedPreferences` store for the app-blocking feature, exposed as `StateFlow`s: master `enabled` flag plus the `allowedPackages` set. Seeds defaults (dialer/SMS/Settings) once via `seedDefaultsIfNeeded`. Read by both the settings UI and `AppBlockAccessibilityService`. Init in `WakeUpApp.onCreate`.

---

## Domain logic (`domain` package)

- `AlarmScheduler.kt`
  - Wraps `AlarmManager` and schedules exact alarm triggers and snooze behavior.
- `NextTriggerCalculator.kt`
  - Computes the next alarm trigger time based on the alarm's time and repeating days mask.
- `AppBlockPolicy.kt`
  - Pure decision for the app-blocking feature: `shouldBlock(foregroundPackage, selfPackage, allowedPackages, alarmActive, featureEnabled)`. Never blocks WakeUp itself, core system packages (`android`, `com.android.systemui`), or allow-listed apps; the home launcher stays blockable so HOME bounces back to the alarm. Unit-tested in `AppBlockPolicyTest`.
- `DeviceOwnerPolicy.kt`
  - Defensively-guarded wrapper around `DevicePolicyManager` for the optional kiosk (lock-task) layer that suppresses the power menu while an alarm rings. `isDeviceOwner()`, `enableAlarmLockdown()` (whitelists our package via `setLockTaskPackages` + `setLockTaskFeatures` with `LOCK_TASK_FEATURE_GLOBAL_ACTIONS` **omitted** so the power menu is hidden), `disableAlarmLockdown()` (restores platform defaults), and `relinquishDeviceOwner()` (escape hatch → `clearDeviceOwnerApp`). Only active when provisioned as device owner via ADB; every call is a logged no-op otherwise, so the app degrades to a normal alarm. Feature-detects API 28 for the lock-task feature flags.

---

## Broadcast receivers (`receiver` package)

- `AlarmReceiver.kt`
  - Receives alarms from `AlarmManager`, launches the ringing UI and foreground service, resets snooze count, reschedules repeating alarms, and disables one-shot alarms.
- `BootReceiver.kt`
  - Reschedules all enabled alarms after device reboot.
- `WakeUpDeviceAdminReceiver.kt`
  - Empty `DeviceAdminReceiver` subclass required so WakeUp can be provisioned as *device owner* over ADB (`dpm set-device-owner com.loic.wakeup/.receiver.WakeUpDeviceAdminReceiver`). Device-owner status unlocks the kiosk lock-task layer (see `DeviceOwnerPolicy`). Inert on any un-provisioned install. Declared in the manifest with `BIND_DEVICE_ADMIN` + a `DEVICE_ADMIN_ENABLED` filter and `@xml/device_admin` metadata.

---

## Alarm service (`service` package)

- `AlarmService.kt`
  - Foreground service that plays the alarm ringtone, vibrates, posts a high-priority notification with full-screen intent, handles snooze, and exposes `RingState` for the UI.
- `AppBlockAccessibilityService.kt`
  - Accessibility service backing the app-blocking feature. On every foreground-app change it consults `AppBlockPolicy`; when an app should be blocked (feature on + alarm ringing + not allow-listed) it relaunches `AlarmRingingActivity`, so no app — nor HOME/recents — can escape the alarm. Inert until the user enables it in Android's accessibility settings.

---

## UI navigation (`ui/nav` package)

- `NavGraph.kt`
  - Compose navigation graph defining routes for alarm list, alarm edit, and NFC settings screens.

---

## UI screens (`ui/screens` package)

- `AlarmListScreen.kt`
  - Main alarm list UI showing existing alarms, enable/disable controls, and navigation to edit and settings.
- `AlarmEditScreen.kt`
  - Screen for creating or editing an alarm, including time, repeat days, label, ringtone, and snooze settings.
- `AlarmRingingActivity.kt`
  - Full-screen ringing activity that shows alarm state, prevents back/volume escape, enables NFC reader mode on resume, and dismisses alarms when the registered NFC tag is scanned. When WakeUp is device owner it also enters a kiosk lock task on ring (`enterKioskIfOwner`, idempotent across recreation via `ActivityManager.lockTaskModeState`) to suppress the power menu; a successful dismiss leaves lock task and restores defaults (`exitKioskIfActive`), with an `onDestroy`/`isFinishing` backstop. No-op when not device owner.
- `NfcSettingsScreen.kt`
  - Settings screen to register, replace, or remove the NFC tag using NFC reader mode, plus buttons for permissions, exact alarm settings, and a button into the app-blocking screen.
- `AppBlockSettingsScreen.kt`
  - Settings screen for the app-blocking feature: master on/off switch, live accessibility-service status with a button into Android's accessibility settings, and a scrollable list of installed apps with checkboxes to build the allow-list. Also hosts the "KIOSK LOCKDOWN" panel showing device-owner status; when WakeUp is device owner it shows a "Clear device owner" button (confirm dialog) that calls `DeviceOwnerPolicy.relinquishDeviceOwner()` to undo the kiosk layer without ADB.

---

## UI theming (`ui/theme` package)

- `Theme.kt`
  - Compose theme definitions, color scheme, typography, and dark-only styling used across the app. Defines the pre-dawn palette plus the aurora glow colors (`DawnGlow`, `IndigoGlow`, `DeepNight`) and liquid-glass tokens (`GlassTint`, `GlassFillFallback`, `GlassEdgeHigh`, `GlassEdgeLow`).
- `Glass.kt`
  - Liquid-glass design system built on the Haze library (`dev.chrisbanes.haze` 1.3.1, real backdrop blur on API 31+, translucent scrim fallback below). Three primitives:
    - `Modifier.auroraSky()` — draws the pre-dawn sky gradient with warm/cool radial glows; put it on the layer also marked `hazeSource`.
    - `Modifier.frostedPanel(shape)` — dark frosted fill (`GlassFill`) under a white sheen (`GlassTint`) + specular hairline, **no** blur; for panels that sit over the static aurora (cards, hero, form sections). The dark fill tames the lower-left dawn glow so muted text stays legible.
    - `Modifier.liquidGlass(hazeState, shape)` — real Haze backdrop blur + specular hairline; reserved for a surface that overlaps moving content (the pinned top bars on the edit/NFC screens the form scrolls under, or the ringing buttons over the animated pulse). The edit/NFC bars use `RectangleShape` (flat full-width strip).
  - Screen pattern (edit/NFC): an inner `Box` carries `auroraSky() + hazeSource(state)` and wraps the scrolling content; the pinned `liquidGlass` bar is a **sibling** overlay drawn over it (source and effect must be siblings, never parent/child). The alarm list has no pinned bar — its title and a compact "next alarm" info strip scroll with the list, directly on the aurora.

---

## ViewModels (`ui/viewmodel` package)

- `AlarmListViewModel.kt`
  - Handles alarm list state, toggling alarm enabled state, and enforces NFC tag registration before enabling alarms.
- `AlarmEditViewModel.kt`
  - Manages alarm creation/editing and prevents saving/enabling alarms if no NFC tag is registered.
- `NfcSettingsViewModel.kt`
  - Coordinates NFC tag scanning state, stores tag UID, clears the registered tag, and disables all alarms when the tag is removed.

---

## Resource files

- `app/src/main/res/values/colors.xml`
  - App color palette definitions.
- `app/src/main/res/values/strings.xml`
  - Localized UI text, NFC prompts, alarm labels, and notification strings.
- `app/src/main/res/values/themes.xml`
  - Theme configuration and style definitions.
- `app/src/main/res/xml/device_admin.xml`
  - Device-admin policy metadata for the kiosk layer (`android:visible="false"`, empty policy set — lock task needs only device-owner status). Referenced by `WakeUpDeviceAdminReceiver` in the manifest.
- `app/src/main/res/xml/data_extraction_rules.xml`
  - Backup and data extraction rules for Android.
- `app/src/main/res/xml/backup_rules.xml`
  - Backup configuration for app data.
- `app/src/main/res/drawable` and `mipmap-*`
  - App icons and image assets used by the app.

---

## Notes

- NFC in this codebase is implemented with reader mode in the ringing activity and settings screen, not with NFC intent filters or foreground dispatch.
- One-shot alarms are disabled after firing; repeating alarms are rescheduled automatically.
- NFC tag registration is required before alarms can be enabled or saved.
