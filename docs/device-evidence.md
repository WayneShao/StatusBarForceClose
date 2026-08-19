# Verified device evidence

## 0.3.0 release candidate

The user confirmed that the current double-tap force-stop behavior works normally on both daily
devices after the settings, persistent Bridge, warm RootService, recovery events, and execution-mode
work. Local verification completed 134 JVM tests with zero failures, Debug/AndroidTest/Release builds,
lint, and the APK component/API 102 contract.

The Launcher alias feature was installed and exercised on the OnePlus 13T (`PKX110`):

- the default state exposed exactly one `.LauncherAlias` Launcher activity;
- enabling `隐藏桌面图标` removed every Launcher resolution while `SettingsActivity` stayed resumed;
- the real `SettingsActivity` remained directly launchable while the alias was disabled;
- disabling the switch restored the Launcher alias;
- an in-place `adb install -r` preserved the hidden component state;
- the device was restored to the visible-icon state after the test.

The Xiaomi device completed the current settings/root/SystemUI handshake check before its wireless
ADB connection became unavailable. The Launcher alias switch is not claimed as tested on Xiaomi in
this record until that device reconnects.

## 0.2.0 release

Evidence was collected from the user's two rooted daily devices. The same Debug APK built from
commit `a75c57d` was installed on both devices and had SHA-256
`635F910FD8334C46C25DC542DC57EDA8D9CCA17892615B36BD3BDDAB72A4ECF0`.

## Xiaomi 17 Ultra for Leica

- codename / model: `nezha` / `25128PNA1C`
- HyperOS: `OS4.0.0.10.XPACNXM`
- Android: 17 / SDK 37
- SystemUI package: `com.android.systemui`
- selected hook: `MIUI_DISPATCH`
- target: WeChat, `com.tencent.mm`, user 0
- bridge process: module UID process created and accepted only the SystemUI calling UID
- libsu process: `RootActivityManagerService`, observed `uid=0`
- force-stop result: `ROOT_SERVICE`
- visible result: `已强制关闭微信`

The SystemUI PID changed only during the explicitly requested scope restart. `lspd` and
`system_server` PIDs remained unchanged during validation.

## OnePlus 13T

- model: `PKX110`
- build: `PKX110_16.0.10.500(CN01)`
- ColorOS: 16 / ROM code `V16.1.0`
- Android: 16 / SDK 36
- SystemUI version: `16.99.12` (`versionCode 169912`)
- selected hook: `OPLUS_INFLATE_LISTENER`
- target: WeChat, `com.tencent.mm`, user 0
- force-stop result: `ROOT_SERVICE`
- visible result: `已强制关闭微信`

The new SystemUI PID recorded three successful `ROOT_SERVICE` results and zero direct-su,
ContentProvider, or Binder-fallback attempts. `lspd` and `system_server` PIDs remained unchanged.
ColorOS did not expose the module/root process direct `Log.println` records through the current ADB
logcat buffer, so this document does not claim a directly observed OnePlus root UID line.

## Scope of the claim

- The two exact builds above are verified.
- User 0 behavior is verified on both devices.
- User 999 and other valid user IDs are covered by JVM tests but are not claimed as new 0.2.0
  device validation.
- No vendor APK, system partition file, `lspd`, or `system_server` was modified or restarted.
