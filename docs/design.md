# Design

## Goal

Provide one removable LSPosed action on supported rooted Android systems: double-tapping the status
bar force-stops the actual foreground application for its actual Android user and shows the target
application label in a SystemUI Toast.

## Boundaries

- The APK has no launcher icon, Activity, settings UI, Provider, Receiver, Android permission, or
  native library.
- Static Xposed scope is only `com.android.systemui`.
- The only Android component is an exported `ForceStopBridgeService`; it validates that the Binder
  calling UID maps to `com.android.systemui` before accepting a request.
- No vendor APK or system partition file is modified, repacked, replaced, or re-signed.

## Event strategies

Hook selection is deterministic and fail-closed:

1. A verified Xiaomi `MiuiPhoneStatusBarView` hierarchy selects `MIUI_DISPATCH` and observes
   `PhoneStatusBarView.dispatchTouchEvent(MotionEvent)`.
2. Otherwise, a standard PhoneStatusBarView plus the verified Oplus marker selects
   `OPLUS_INFLATE_LISTENER`; `onFinishInflate()` attaches a non-consuming listener.
3. Unknown structures select `UNSUPPORTED` and install no touch hook.

Both strategies feed the same `DoubleTapDetector` and never replace the original touch result.

## Target and user

The resolver reads the current foreground task, package name, label, task ID, and real user ID. It
does not replace missing or cloned-user data with user 0. Android core, SystemUI, MiuiHome, and the
active input method are protected; Settings and the module package are not protected.

## Root execution

1. SystemUI binds the module's explicit bridge Service.
2. The module process validates the caller and invokes libsu `RootService.bind()` on its main
   thread.
3. libsu starts `RootActivityManagerService` as root.
4. The root service applies hidden-API exemptions required by target SDK 37, obtains the system
   ActivityManager Binder, and invokes `forceStopPackage(packageName, userId)`.
5. A successful remote result becomes `ROOT_SERVICE`; only root failure permits the existing
   SystemUI Binder fallback.
6. SystemUI shows the dynamic Toast only after a successful result.

This root backend is vendor-independent. Only the SystemUI event strategy is vendor-specific.

## Diagnostics

Debug and manually dispatched signed test builds retain structured events for hook selection,
target resolution, bridge connection, caller UID, root process startup, force-stop result, fallback,
Toast, and request release. Tag-triggered Release builds compile diagnostics out and R8 removes the
unreachable strings.
