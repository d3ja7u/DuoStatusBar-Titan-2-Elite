<details open>
<summary><b>English</b></summary>

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

</details>

<details>
<summary><b>中文</b></summary>

# DuoStatusBar（Titan 2 Elite）

一个精简的 LSPosed 模块：把时间、当前网络信号和电池圆环合成一个右侧指示器，并将原左侧时钟合并到右侧。参考 iPhone Duo 的电池圆环思路，针对本机 Android 16 / MtkSystemUI 的三套布局实现。

## 功能

- 时间 + Wi-Fi/移动信号 + 电池圆环。
- 普通状态栏、通知与控制中心、锁屏三个独立开关。
- 圆环内显示电量数字的开关。
- 蓝牙、静音/振动、其他状态栏图标的隐藏开关。
- 沿用系统时钟文字、网络图标和颜色；支持 12/24 小时制。
- 电量低时圆环变红，充电时圆环变绿。
- 自动扩展目标父容器高度，避免电量数字在普通状态栏/通知中心被裁切。
- 关闭某个位置的开关后，恢复该位置被隐藏的原生视图及原始布局高度。
- 仅 hook `com.android.systemui`，不修改系统分区，不写入手机设置数据库。

## 适配入口

- 普通状态栏：`PhoneStatusBarView` / `CollapsedStatusBarFragment`
- 通知与控制中心：`NotificationPanelView` 内的 `split_shade_status_bar`
- 锁屏：`KeyguardStatusBarView`

设置通过只读 `ContentProvider` 在模块 App 与 SystemUI 进程间同步，避免依赖跨进程可读 SharedPreferences 文件。

## 构建

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 安装与启用

1. 安装 APK。
2. 在 LSPosed 中启用“DuoStatusBar”，作用域只选择 SystemUI。
3. 重启 SystemUI。
4. 打开“DuoStatusBar”App，选择需要修改的位置。

当前稳定版本：`1.3.10`（`versionCode 17`）。已在 Titan 2 Elite（Android 16 / MtkSystemUI）真机验证锁屏、通知与控制中心、普通状态栏三个场景及图标隐藏开关。普通状态栏与锁屏通过扩展图标行高度容纳 28dp 圆环；通知与控制中心因 header 由 MotionLayout 驱动、扩高会被重测覆盖，改为按可用高度自适应缩小圆环。

</details>
