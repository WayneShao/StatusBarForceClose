# ColorOS 16 And PKX110 Compatibility Design

## Goal

Extend the existing StatusBarForceClose APK to support OnePlus 13T (PKX110)
on ColorOS 16 while preserving the verified Xiaomi 17 Ultra for Leica HyperOS
4 behavior.

## Verified Inputs

The live PKX110 reports Android 16 / SDK 36, ColorOS ROM code `V16.1.0`, build
`PKX110_16.0.10.500(CN01)`, SystemUI version `16.99.12` (`versionCode 169912`),
and users 0 and 999. Its SystemUI APK SHA-256 is
`1bf1a98aaf4cccce1d64ea3e10cba3ad82bfd654bebe11996697e6a9e61002cc`.

Static inspection proves that this SystemUI contains:

- `com.android.systemui.statusbar.phone.PhoneStatusBarView`
- `PhoneStatusBarView.onFinishInflate()`
- `com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl`

The decompiled `onFinishInflate()` does not install an `OnTouchListener`.
LuckyTool uses this lifecycle method to attach its double-tap screen-off
listener on ColorOS. StatusBarForceClose uses the same hook boundary but keeps
its own detector and force-stop implementation. No LuckyTool GPLv3 source is
copied into this MIT project.

## Alternatives

The selected approach is a strict dual strategy:

1. Keep the existing Xiaomi `MiuiPhoneStatusBarView` hierarchy check and
   `PhoneStatusBarView.dispatchTouchEvent(MotionEvent)` interception.
2. When the Xiaomi structure is absent, require both the standard
   `PhoneStatusBarView` and the Oplus marker class, then hook
   `PhoneStatusBarView.onFinishInflate()` and attach a non-consuming listener.

A single listener strategy for both ROMs was rejected because it would replace
an already verified Xiaomi boundary. A generic AOSP fallback was rejected
because it would install on unverified SystemUI implementations.

## Hook Selection

Selection is deterministic and fail-closed:

1. A valid Xiaomi subclass hierarchy selects `MIUI_DISPATCH`.
2. Otherwise, a PhoneStatusBarView plus Oplus marker selects
   `OPLUS_INFLATE_LISTENER`.
3. All other structures select `UNSUPPORTED` and install no touch hook.

Debug diagnostics record the selected strategy, the hooked class and method,
listener attachment, and any missing or incompatible structure.

## Oplus Event Flow

The Oplus lifecycle interceptor attaches an `OnTouchListener` to each inflated
PhoneStatusBarView. The listener forwards every MotionEvent to the existing
`observeTouch()` method and always returns false, preserving the original event
chain. The existing per-view `DoubleTapDetector`, Android-configured double-tap
slop, 250 ms tap duration, request serialization, foreground task resolution,
user 0/999 handling, root/Binder fallback, protection policy, and dynamic Toast
remain shared by both strategies.

The implementation does not call `performClick()` for each ACTION_DOWN because
the module observes rather than owns the status bar click contract.

## SDK And Packaging

- `compileSdk` remains 37.
- `targetSdk` remains 37.
- `minSdk` changes from 37 to 36 so the APK can install on PKX110.
- libxposed API 102 metadata and the static `com.android.systemui` scope remain.
- The APK still declares no launcher icon, component, or Android permission.
- CI and release validation must require minSdk 36 and targetSdk 37.
- The compatibility release is versionCode 3 / versionName 0.2.0.

## Testing

TDD covers strategy priority and fail-closed selection. Existing detector,
policy, force-stop coordinator, message, and task-user tests remain unchanged.
The full local gate is `clean testDebugUnitTest lintDebug assembleDebug
assembleRelease` followed by APK metadata and diagnostic stripping checks.

A manually dispatched signed Actions artifact is installed on both devices
before release. Device validation covers an ordinary user-0 app and, where
available, a cloned user-999 app, confirms the named Toast, verifies the logged
strategy and user ID, and checks that SystemUI remains stable. Only SystemUI may
be restarted; neither phone is rebooted.

## Documentation And Release

README records that the owner daily carries Xiaomi 17 Ultra for Leica on
HyperOS 4 and OnePlus 13T (PKX110) on ColorOS 16. The statement that the module
is verified on both phones is added only after the same signed 0.2.0 test build
passes device validation. Compatibility claims remain limited to the recorded
builds.

After both devices pass, merge the adaptation to main, create tag `3-0.2.0`,
publish the signed Release with full notes, and independently validate its APK,
checksum, certificate, metadata, and diagnostics boundary.
