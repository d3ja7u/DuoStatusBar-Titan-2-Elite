English · [中文](README_CN.md)

# DuoStatusBar (Titan 2 Elite)

A lightweight LSPosed module that combines the clock, network signal, and battery ring into a single right-side indicator, and merges the original left clock into the right side. The battery ring design is inspired by the iPhone Duo, adapted for the three layouts of Android 16 / MtkSystemUI on this device.

## Features

- Clock + Wi-Fi/mobile signal + battery ring.
- Independent toggles for the status bar, notification & control center, and lock screen.
- Toggle to show the battery percentage inside the ring.
- Toggles to hide Bluetooth, silent/vibrate, and other status bar icons.
- Reuses system clock text, network icons, and colors; supports 12/24-hour format.
- The ring turns red when the battery is low, and green while charging.
- Automatically expands the target parent container height to prevent the percentage from being clipped.
- Disabling a scene restores the hidden native views and the original layout height.
- Only hooks `com.android.systemui`; no system partition changes, no writes to the device settings database.

## Hook Targets

- Status bar: `PhoneStatusBarView` / `CollapsedStatusBarFragment`
- Notification & control center: `split_shade_status_bar` inside `NotificationPanelView`
- Lock screen: `KeyguardStatusBarView`

Settings are synced between the module app and the SystemUI process through a read-only `ContentProvider`, avoiding reliance on cross-process readable SharedPreferences files.

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install & Enable

1. Install the APK.
2. Enable "DuoStatusBar" in LSPosed, with SystemUI as the only scope.
3. Restart SystemUI.
4. Open the "DuoStatusBar" app and pick the scenes to modify.

Current stable version: `1.3.10` (`versionCode 17`). Verified on a Titan 2 Elite (Android 16 / MtkSystemUI) for the lock screen, notification & control center, and status bar scenes, plus the icon-hiding toggles. The status bar and lock screen expand the icon row height to fit the 28dp ring; the notification & control center scales the ring down based on available height because its header is driven by MotionLayout.
