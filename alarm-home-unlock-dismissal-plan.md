# Alarm Home/Unlock Dismissal Plan

## Summary

Investigate and harden the alarm flow where pressing Home during a ringing alarm, then unlocking the phone, appears to dismiss the alarm without an intentional NFC scan.

The likely cause is that NFC becomes available immediately after unlock on Samsung devices, so if the phone is near the registered tag, `onTagDiscovered()` can fire right after unlock and call `dismiss()`. There is also a lifecycle issue: `AlarmRingingActivity.onStop()` relaunches the activity without preserving `AlarmService.EXTRA_ALARM_ID`, which can make the restored ringing UI lose alarm-specific context.

## Key Changes

- Add temporary debug logging around `AlarmRingingActivity.onResume`, `onStop`, `onTagDiscovered`, `handleScan`, and `dismiss()` to confirm whether unlock triggers an NFC read.
- Preserve `AlarmService.EXTRA_ALARM_ID` in the `onStop()` relaunch intent, using the current `alarmId` or `AlarmService.runningAlarmId`.
- Add `onNewIntent()` handling for `AlarmRingingActivity`, since it uses `launchMode="singleTask"`, and update `alarmId` from any new intent that carries the alarm extra.
- Add a dismiss cause log or guard so future reports distinguish NFC-match dismissal from no-tag button dismissal.

## Test Plan

- Ring an NFC-required alarm, press Home, unlock with the phone away from the tag, and verify the alarm keeps ringing.
- Repeat with the phone near the registered tag and verify logs show `onTagDiscovered`, then `dismiss()`.
- Press Home and unlock repeatedly, then verify the ringing screen still references the correct alarm ID.
- Run `./gradlew test`.

## Assumptions

- The alarm is not configured with "Use no tag."
- "Dismissed" means the ringtone and foreground service stop, not only that a one-shot alarm toggle becomes disabled after firing.
