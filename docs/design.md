# StatusBar Force Close Design

## Goal

Provide one fixed action on HyperOS 4: double-tapping the status bar force-stops
the current foreground application.

## Boundary

- Independent LSPosed/libxposed API 102 APK.
- Scope is only `com.android.systemui`.
- No launcher activity, icon, settings UI, service, receiver, or root daemon.
- Does not modify CustoMIUIzer or HyperCeiler.
- Observes the existing status-bar touch chain without consuming or replacing it.

## Runtime Flow

1. Verify that `MiuiPhoneStatusBarView` still extends `PhoneStatusBarView`, then
   observe `PhoneStatusBarView.dispatchTouchEvent(MotionEvent)` after its
   original implementation returns.
2. Feed raw touch data to a per-view double-tap detector.
3. Resolve the top running task and the current SystemUI user ID.
4. Reject protected packages and the active input method.
5. Try `su -c am force-stop --user USER_ID PACKAGE` on a background thread.
6. If `su` is missing, denied, times out, or exits unsuccessfully, invoke the
   system ActivityManager Binder for the same package and user.
7. After a successful force-stop, show a short SystemUI Toast using the target
   application's label, such as `已强制关闭微信`.

The module fails closed when the status-bar class, top task, package, user, or
ActivityManager method cannot be resolved.

Version 0.1 targets the foreground application of the current Android user. It
does not attempt cross-profile or managed-profile force-stop operations.

Because the module code runs inside SystemUI, a root manager may attribute the
`su` request to SystemUI. Binder fallback avoids making root approval a hard
requirement.

## Debug Diagnostics

Debug builds log the complete decision path with stable `event=` names to both
the libxposed log and Android logcat under the `StatusBarForceClose` tag. No
individual touch events are logged; only a recognized double tap and its
resulting request are recorded. The build-time `DIAGNOSTICS_ENABLED` constant
disables the diagnostic sink in release builds.
