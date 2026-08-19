# Design

## Goal

Provide one removable LSPosed action on supported rooted Android systems: double-tapping the status
bar force-stops the actual foreground application for its actual Android user and shows the target
application label in a SystemUI Toast.

## Boundaries

- The APK has one launcher settings Activity, one adaptive icon, and one exported Bridge Service.
- It has no Provider, manifest Receiver, Android permission, foreground Service, boot receiver, or
  native library. Screen and unlock signals use process-lifetime dynamic receivers in SystemUI.
- Static Xposed scope is only `com.android.systemui`.
- `ForceStopBridgeService` separates module-UID settings operations from authenticated SystemUI
  operations. SystemUI requires a matching protocol, process generation, and opaque session token.
- No vendor APK or system partition file is modified, repacked, replaced, or re-signed.
- Durable state never contains a foreground package, label, Activity, task ID, or user history.

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

## Runtime and execution

1. SystemUI keeps one explicit `BIND_AUTO_CREATE` connection to the Bridge for its process lifetime.
2. Registration establishes an authenticated generation/token session and atomically delivers the
   latest configuration.
3. The Bridge warms one libsu RootService connection without blocking gesture processing.
4. The root process applies the required hidden-API exemptions, obtains ActivityManager Binder,
   and invokes `forceStopPackage(packageName, userId)`.
5. SystemUI probes its local Binder capability once per process. A permission rejection fuses that
   backend for the rest of the process; unsupported structures remain fail-closed.
6. `AUTO`, `ROOT_FIRST`, `SYSTEM_UI_FIRST`, `ROOT_ONLY`, and `SYSTEM_UI_ONLY` produce a typed,
   deterministic execution plan. Only permitted backend failures fall through to another step.
7. SystemUI shows the dynamic Toast only after a typed successful result.

This root backend is vendor-independent. Only the SystemUI event strategy is vendor-specific.

## Recovery

- Opening the settings Activity generates one saved-state token and permits one idempotent root
  connection request. Rotation redelivers the same token until receipt.
- A task-front callback requests immediate recovery without transmitting task data.
- `USER_PRESENT` requests immediate recovery; `SCREEN_ON` waits 750 ms.
- All signals share a five-second monotonic cooldown and are suppressed while connected,
  connecting, denied, or incompatible.
- Automatic Bridge reconnect is bounded to one attempt. A later explicit signal can open a new
  recovery window; there is no timer loop or boot component.

## Settings and persistence

The native Activity observes SystemUI connection, RootService state, local SystemUI capability,
background-protection preference, and the last successful backend/elapsed time. It writes only the
five-mode configuration and the background-protection switch.

The Bridge store uses a versioned schema and synchronous commits around root authorization epochs,
SystemUI generations, configuration revisions, optimization transitions, and minimal last-result
state. Background protection captures original Doze/AppOps values, mutates only module-owned items,
and restores only values that are still owned by this module. Corrupt or unknown durable state fails
closed.

## Diagnostics

Debug and manually dispatched signed test builds retain structured events for hook selection,
target resolution, bridge connection, caller UID, root process startup, force-stop result, fallback,
Toast, and request release. Tag-triggered Release builds compile diagnostics out and R8 removes the
unreachable strings.
