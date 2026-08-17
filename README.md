# StatusBar Force Close

Minimal HyperOS 4 LSPosed module. Double-tap the status bar to force-stop the
current foreground application.

Version 0.1 targets HyperOS 4 on Android 17 (SDK 37) and libxposed API 102.

The APK intentionally has no launcher icon, activity, settings UI, service, or
background process. Its only Xposed scope is `com.android.systemui`.

The action first runs `su -c am force-stop` for the foreground package. If root
execution is unavailable or fails, it falls back to the ActivityManager Binder
available to SystemUI.

After a successful action, SystemUI shows `已强制关闭<应用名>` using the actual
label of the application that was stopped.

## Build

```powershell
$env:JAVA_HOME='D:\Backup\Desktop\HyperOS4\tools\jdk25\jdk-25.0.4+7'
$env:ANDROID_HOME='D:\Backup\Desktop\HyperOS4\tools\android-sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon --offline
```

The local test artifact is `dist/StatusBarForceClose-v0.1.0-debug.apk`. It uses
the local Android debug certificate and is intended for the first device test.
All structured diagnostics are guarded by the build-time
`BuildConfig.DIAGNOSTICS_ENABLED` constant (`true` for debug, `false` for
release). Release builds emit none of these logs.

Debug diagnostics are available in both the LSPosed module log and logcat:

```powershell
adb -s SERIAL logcat -v threadtime -s StatusBarForceClose
```

## Enable

Install the APK, enable it in an API102-compatible LSPosed implementation, and
keep its static scope limited to `com.android.systemui`. A SystemUI process
restart or device reboot is required before the initial Hook becomes active.

No Xiaomi APK or system file is patched by this module.
