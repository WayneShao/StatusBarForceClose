# Bridge recovery investigation, 2026-09-23

## Evidence and limits

The installed 0.3.0 on OnePlus PKX110/Android 16 had a bound SystemUI client,
a frozen UID cgroup, and repeated `OTHER KILLS BY SYSTEM` exits. After the user
unlocked the device, the bridge and root PIDs changed and force-stop worked again.
The UID cgroup still read frozen. The freezer owner and the cause of each earlier
failure are not established by those snapshots.

A controlled test used the Settings homepage (no edited settings or user documents).
Writing the already-set UID freeze flag did not prevent a successful double tap.
Temporarily sending STOP to only the bridge process did: the SystemUI request worker
remained in `BinderProxy.transact -> SystemUiRuntime.forceStop` after five seconds.
After CONT, the pending request executed. SystemUI itself was not restarted.
This proves the unbounded synchronous wait and delayed execution failure mode;
it does not prove listener replacement or identify a freezer component.

The persisted optimization journal also said APPLIED while the actual Doze list
did not include the module. `apply()` skipped APPLIED/UNCHANGED entries forever.

## Changes

- One shared two-lane transport for all SystemUI-to-bridge calls: 2-second queries,
  4-second force-stop calls. A timed-out native Binder transaction keeps its lane
  until it really returns. Full lanes reject new work; they do not spawn replacements
  or queue additional native transactions.
- Request deadlines propagate to the bridge, root connection wait and final root
  helper. Expired work is discarded after acquiring the helper monitor and after
  initialization, before calling AMS. Expiration also prevents local fallback.
  Already-submitted AMS transactions cannot be revoked by this module.
- Binding has a 5-second deadline. Each bind has a distinct callback epoch, so old
  registration results, disconnections and callbacks cannot replace newer state.
  Existing signal-driven, bounded reconnection remains in use.
- ROOT_ONLY is preserved. No gesture-hook replacement, freezer bypass, background
  manager disabling, new service or permission is introduced.
- Recheck optimization values when applying an active journal, preserving rollback
  ownership. Settings reads actual PowerManager/AppOps state independently of that
  journal and explicitly separates Doze from vendor/third-party freeze protection.
- Rate-limited release health events contain neither target names nor session
  identifiers. Detailed diagnostics remain a separate build option.
- Bridge protocol 5 requires reloading the injected SystemUI code after updating.

## Validation before deployment

The timeout/lane and Doze-drift regression tests failed before the fixes. The full
host suite, lint, signed R8 build and APK contract passed after the fixes. Independent
review found two further deadline holes (root monitor wait and local fallback);
both were fixed. The signing certificate matches both installed 0.3.0 packages.

Device acceptance is recorded separately after deployment. Local private evidence
and rollback APK/preferences are under the parent workspace's
`evidence/statusbar-20260923`; private snapshots are not committed here.
