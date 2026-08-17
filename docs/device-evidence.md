# HyperOS 4 SystemUI Evidence

Read-only snapshot collected on 2026-08-17 from the user's Xiaomi `nezha`:

- Android SDK: 37
- SystemUI package: `com.android.systemui`
- SystemUI version: `17.03.260226.r` (`versionCode=202602260`)
- APK path: `/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk`
- Pulled APK SHA-256:
  `9944994E50A94C077BB7EB8315986554305DC00C39B1E7E2B9C62512941FA7C5`
- `MiuiPhoneStatusBarView` is a Java class extending `PhoneStatusBarView`.
- `PhoneStatusBarView` declares final
  `dispatchTouchEvent(MotionEvent)`, `onInterceptTouchEvent(MotionEvent)`, and
  final `onTouchEvent(MotionEvent)` methods.
- The SystemUI manifest requests `REAL_GET_TASKS`, `MANAGE_ACTIVITY_TASKS`, and
  `FORCE_STOP_PACKAGES`.

The device APK was copied only for local analysis. No system APK or other Xiaomi
file was changed, replaced, repacked, or re-signed.
