# Foreground Task User And Policy Fix Design

## Goal

Make double-tap force-close target the foreground task's actual Android user,
including HyperOS dual-app user 999, while allowing Settings and this module to
be closed like ordinary foreground applications.

## Root Cause

`TopTaskResolver` currently derives the target user from `Process.myUid()`.
The resolver runs inside SystemUI, so that UID identifies SystemUI's user rather
than the foreground task's user. Both force-stop implementations already accept
and use a supplied user ID, which means the incorrect value originates entirely
at foreground-task resolution.

## Policy

The protected package set remains limited to packages whose termination would
break the gesture host or the core system interaction path:

- `android`
- `com.android.systemui`
- `com.miui.home`
- the active input method package

`com.android.settings` and `com.wayne.statusbarforceclose` are not protected.
They follow the same force-stop path as any other ordinary foreground app.

## Task User Resolution

Read the hidden `userId` field from the actual
`ActivityManager.RunningTaskInfo` returned by `getRunningTasks(1)`. The Android
SDK stubs do not expose this runtime field, so access is isolated in a small
reflection helper that walks the task object's class hierarchy. Walking the
hierarchy is required because the runtime field is owned by a superclass on
current Android builds.

Any non-negative user ID is accepted. This directly supports user 0, HyperOS
dual-app user 999, and future valid users without package-name heuristics.

If the field is absent, inaccessible, not an integer, or negative, resolution
fails closed: no root command and no Binder force-stop call is attempted. There
is deliberately no fallback to user 0 or `Process.myUid()`, because either
fallback can terminate the wrong profile's application.

## Data Flow

1. Resolve the top `RunningTaskInfo` and foreground package.
2. Apply the protected-package and active-input-method policy.
3. Resolve `userId` from that same task object.
4. Resolve the display label and construct `TopTask(package, userId, label)`.
5. Pass the unchanged user ID through `ForceStopCoordinator` to root first and
   Binder second.
6. Show the existing success Toast using the resolved application label.

## Diagnostics

Debug builds log the task ID, package, and resolved user ID. If user resolution
fails, they log a distinct `target_lookup_empty` reason plus task ID and runtime
task class. Release builds continue compiling diagnostics out through the
existing `BuildConfig.DIAGNOSTICS_ENABLED` switch.

## Tests

JVM tests cover:

- Settings and the module package are permitted.
- Android, SystemUI, MiuiHome, missing packages, and the active input method
  remain protected.
- Task user IDs 0, 999, and another valid secondary user are preserved.
- A field inherited from a fake task superclass is found.
- Missing, wrongly typed, and negative fields fail closed.

The change is developed test-first. After unit tests pass, the full local gate
is `clean`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and
`assembleRelease`. Versioning advances to `versionCode = 2` and
`versionName = 0.1.1`. Per the subsequent release instruction, the source
Release may be published after local review, the full build gate, and cloud CI
all pass. Publishing does not count as new device evidence, and no APK is
installed on the phone as part of this release operation.

## Device Acceptance Criteria

- A normal user-0 app logs and executes with `user=0`.
- A dual app logs and executes with `user=999`.
- Only the foreground profile instance is force-stopped.
- Settings and this module can be force-stopped.
- SystemUI, MiuiHome, Android core, and the active input method remain rejected.
- Success Toast names the actual force-stopped application.
