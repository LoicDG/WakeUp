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
- `TagFailsafeEntity.kt`
  - Room entity (`tag_failsafes`) for the per-tag location failsafe, keyed by tag UID: `enabled`, daily check `hour`/`minute`, and the tag's spot (`latitude`/`longitude`, null until set). Added in DB version 5 (`MIGRATION_4_5`).
- `TagFailsafeDao.kt` / `TagFailsafeRepository.kt`
  - DAO (observe all, get by UID, get enabled, upsert, delete) and its thin repository wrapper.
- `AppBlockStore.kt`
  - Plain-`SharedPreferences` store for the app-blocking feature, exposed as `StateFlow`s: master `enabled` flag, the `allowedPackages` set, plus `powerMenuGuardEnabled` (the best-effort power-menu guard toggle — **on by default**, independent of app blocking but served by the same accessibility service). Seeds defaults (dialer/SMS/Settings) once via `seedDefaultsIfNeeded`. Read by both the settings UI and `AppBlockAccessibilityService`. Init in `WakeUpApp.onCreate`.

---

## Domain logic (`domain` package)

- `AlarmScheduler.kt`
  - Wraps `AlarmManager` and schedules exact alarm triggers and snooze behavior.
- `NextTriggerCalculator.kt`
  - Computes the next alarm trigger time based on the alarm's time and repeating days mask.
- `AppBlockPolicy.kt`
  - Pure decision for the app-blocking feature: `shouldBlock(foregroundPackage, selfPackage, allowedPackages, alarmActive, featureEnabled)`. Never blocks WakeUp itself, core system packages (`android`, `com.android.systemui`), or allow-listed apps; the home launcher stays blockable so HOME bounces back to the alarm. Unit-tested in `AppBlockPolicyTest`.
- `AlarmTagRequirement.kt`
  - Tag rules on `AlarmEntity`: `requiresGlobalTag`, `canActivateWithGlobalTag`, `effectiveTagUid` (custom tag, else global, none for no-tag alarms) and `registeredTagUids` (global + every custom tag). Unit-tested in `AlarmTagRequirementTest`.
- `AlarmDeactivator.kt`
  - Shared "deactivate": recurring alarms skip only their next occurrence (temporary disable + re-enable), one-shot alarms turn off. Used by the reminder notification action and the location failsafe.
- `FailsafePolicy.kt`
  - Pure decision logic for the location failsafe: `isAway(distance, accuracy)` (only when the whole accuracy circle is beyond 100 m) and `alarmsToDeactivate(...)` (the tag's enabled alarms that ring before the next check, minus the ringing one). Also the `TagFailsafeEntity.spot` extension. Unit-tested in `FailsafePolicyTest`.
- `GeoPoint.kt`
  - Lat/lng value type: haversine `distanceTo`, `format()` and `parse("lat, lng")` for pasted coordinates. Unit-tested in `GeoPointTest`.
- `CurrentLocation.kt`
  - `CurrentLocation.get(context)`: one-shot fix via the framework `LocationManager` (fused/GPS/network in parallel, early exit on an accurate fix, recent last-known fallback). `LocationAccess`: precise / "all the time" permission checks.
- `PowerMenuPolicy.kt`
  - Pure decision for the best-effort power-menu guard: `isPowerMenu(packageName, className)` heuristically matches the System UI global-actions/power-menu window (markers like `globalaction`/`powermenu`/`shutdown`), and `shouldDismiss(packageName, className, alarmActive, guardEnabled)` gates that on an active alarm + the guard being on. Backs `AppBlockAccessibilityService`'s power-menu dismissal. Unit-tested in `PowerMenuPolicyTest`.

---

## Broadcast receivers (`receiver` package)

- `AlarmReceiver.kt`
  - Receives alarms from `AlarmManager`, launches the ringing UI and foreground service, resets snooze count, reschedules repeating alarms, and disables one-shot alarms.
- `FailsafeReceiver.kt`
  - Fired daily at a tag failsafe's check time. Drops failsafes of tags that are no longer registered, queues tomorrow's check, and starts `FailsafeService` only when one of the tag's alarms would be deactivated (or posts a notification if background location access is missing).
- `BootReceiver.kt`
  - Reschedules all enabled alarms and failsafe checks after device reboot.
- `AlarmService.kt`
  - Foreground service that plays the alarm ringtone, vibrates, posts a high-priority notification with full-screen intent, handles snooze, and exposes `RingState` for the UI.
- `FailsafeService.kt`
  - Short-lived foreground service (type `location`) that runs one failsafe check: gets a fix, and if the device is clearly away from the tag's spot deactivates the tag's upcoming alarms via `AlarmDeactivator`. No fix → alarms stay on.
- `FailsafeNotifications.kt`
  - The failsafe's notifications: silent "checking" FGS notification, and per-tag outcome notifications (alarms deactivated with distance + list, couldn't locate, permission missing).
- `AppBlockAccessibilityService.kt`
  - Accessibility service backing the app-blocking feature. On every foreground-app change it consults `AppBlockPolicy`; when an app should be blocked (feature on + alarm ringing + not allow-listed) it relaunches `AlarmRingingActivity`, so no app — nor HOME/recents — can escape the alarm. It also runs the best-effort power-menu guard: when `PowerMenuPolicy.shouldDismiss(...)` matches the System UI power menu during an alarm, it fires `GLOBAL_ACTION_BACK` (after a ~150 ms delay — the dialog must take input focus first, or the key is swallowed by the ringing activity) to close it and pulls the ringing screen back. Inert until the user enables it in Android's accessibility settings.

---

## UI navigation (`ui/nav` package)

- `NavGraph.kt`
  - Compose navigation graph defining routes for alarm list, alarm edit, settings, app-blocking and failsafe screens.

---

## UI screens (`ui/screens` package)

- `AlarmListScreen.kt`
  - Main alarm list UI showing existing alarms, enable/disable controls, and navigation to edit and settings.
- `AlarmEditScreen.kt`
  - Screen for creating or editing an alarm, including time, repeat days, label, ringtone, and snooze settings.
- `AlarmRingingActivity.kt`
  - Full-screen ringing activity that shows alarm state, prevents back/volume escape, enables NFC reader mode on resume, and dismisses alarms when the registered NFC tag is scanned.
- `NfcSettingsScreen.kt`
  - Settings screen to register, replace, or remove the NFC tag using NFC reader mode, plus buttons for permissions, exact alarm settings, and buttons into the failsafe and app-blocking screens.
- `FailsafeSettingsScreen.kt`
  - Per-tag location failsafe settings: an explainer, a location-access panel (precise → "Allow all the time", re-checked on resume), and one card per registered tag showing its alarms, an on/off switch, the daily check time (Material time-picker dialog) and the tag's spot (set from the current location or typed/pasted coordinates, viewable on a map).
- `AppBlockSettingsScreen.kt`
  - Settings screen for the app-blocking feature: master on/off switch, live accessibility-service status with a button into Android's accessibility settings, and a scrollable list of installed apps with checkboxes to build the allow-list. Also hosts a "POWER MENU" panel (switch for the best-effort power-menu guard).

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
  - Coordinates NFC tag scanning state, stores tag UID, clears the registered tag, and disables all alarms when the tag is removed. Replacing the global tag carries its failsafe over to the new UID.
- `FailsafeSettingsViewModel.kt`
  - Combines alarms + failsafes into one `TagFailsafeItem` per registered tag; edits failsafes (serialised read-modify-write) and reschedules their checks; fills a tag's spot from the current location.

---

## Resource files

- `app/src/main/res/values/colors.xml`
  - App color palette definitions.
- `app/src/main/res/values/strings.xml`
  - Localized UI text, NFC prompts, alarm labels, and notification strings.
- `app/src/main/res/values/themes.xml`
  - Theme configuration and style definitions.
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
