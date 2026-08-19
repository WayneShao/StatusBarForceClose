# Settings, Root Warmup, and Execution Policy Design

## Status

Approved direction from the user on 2026-08-19. This document defines the
design only. It does not claim implementation or device validation.

## Context

Version 0.2.0 has no launcher Activity. Every gesture creates a temporary
SystemUI-to-module binding, asks the module process to bind libsu RootService,
performs the force-stop, and immediately releases the SystemUI binding. Once no
client holds the module Service, Android can destroy the module process and the
RootService connection. A later gesture can therefore pay the complete process
and root-service startup cost again.

The new design adds a small settings entry, keeps the root path warm for the
SystemUI lifetime, classifies the direct SystemUI capability once per SystemUI
process, and lets the user select the execution policy without assuming that
root is inherently slower than the direct SystemUI Binder path.

## Goals

- Add a simple launcher settings Activity so the application can be opened and
  managed in vendor background-protection settings.
- Display the live RootService connection without treating an authorization
  request result as a separate product state.
- Keep the module bridge and RootService warm for the SystemUI lifetime.
- Offer explicit execution modes while retaining a useful automatic mode.
- Skip direct SystemUI force-stop attempts when SystemUI clearly lacks the
  required platform permission.
- Permanently fuse off a rejected SystemUI Binder backend for the remainder of
  the current SystemUI process.
- Add a default-enabled, reversible background-optimization switch.
- Preserve the existing real-user-ID handling, protected-target policy,
  supported SystemUI hooks, dynamic success Toast, API 102 metadata, and
  fail-closed behavior.

## Non-goals

- No foreground Service, persistent notification, boot receiver, periodic job,
  alarm, wake lock, independent daemon, socket server, or new hook framework.
- No modification of SystemUI or any vendor APK or system partition file.
- No destructive startup probe that force-stops an application merely to test
  whether SystemUI has permission.
- No periodic keepalive timer, heartbeat alarm, usage-stats polling,
  AccessibilityService, or hooks in every application's Activity lifecycle.
- No vendor-private AppOps numbers, hard-coded Xiaomi or Oplus process-importance
  values, or direct writes to `oom_score_adj`.
- No claim that one execution backend is universally faster.

## Terminology

The user-facing UI must avoid the ambiguous word `Bridge`:

- **SystemUI backend**: the existing `BinderForceStopMethod`, which invokes
  ActivityManager directly from the injected SystemUI process without root.
- **Root backend**: SystemUI calls `ForceStopBridgeService`; the module process
  then calls the libsu `RootActivityManagerService` running as UID 0.
- **Bridge Service**: transport and lifecycle owner for the root backend. It is
  not an alternative to root.

## Approaches Considered

### SystemUI-owned persistent binding (selected)

SystemUI holds one explicit binding to `ForceStopBridgeService`. The bridge
holds one libsu RootService binding. Android therefore relates the module
process importance to the always-running SystemUI client without a foreground
notification or a second daemon.

### Foreground Service

A foreground Service would be explicit but would require a permanent
notification, additional permissions and lifecycle code, and more vendor
compatibility work. It is unnecessary while SystemUI can own the binding.

### Independent root daemon

A separate daemon would add upgrade handshakes, stale-process cleanup, protocol
versioning, and failure recovery while duplicating libsu RootService. It is not
justified for this feature.

## Components

### Settings Activity

Add one exported launcher Activity owned by the module package. It contains a
quiet settings list rather than a dashboard or onboarding flow. The screen has
four groups:

1. **Runtime status**
   - Module/SystemUI connection: connected or disconnected.
   - Root service: connecting, connected, or not connected. `DENIED`,
     `INCOMPATIBLE`, and transient errors are intentionally rendered as
     `Not connected` rather than authorization-result UI.
   - SystemUI backend: supported, unavailable, or disabled after rejection.
   - Last successful execution: backend and elapsed milliseconds.
2. **Execution mode**
   - A single-choice row opening a five-item selection dialog.
3. **Background optimization**
   - One default-enabled switch.
   - Current Doze and background-AppOps status.
4. **System settings**
   - Open application details.
   - Open battery-optimization settings.
   - Open the most specific available vendor/background-management page, with
     application details as the fallback.

The root warm connection has no user-facing disable switch. There is no feature
tutorial, root-result dialog, or marketing copy in the Activity.

A fresh settings-screen creation with `savedInstanceState == null` requests one
RootService connection. Some root managers may show a prompt; KernelSU may
require the user to grant the package manually in its own manager. The module
does not distinguish managers, open manager applications, or maintain
manager-specific authorization results. The request result does not start any
additional UI or workflow. The screen reflects only the live connection states
`Connecting`, `Connected`, and `Not connected`. Rotation, state restoration,
and ordinary resume do not create a new authorization attempt. After changing
authorization externally, closing and freshly opening the screen performs one
new request. There is no automatic retry loop.

The default resources are English and `values-zh-rCN` provides Simplified
Chinese labels. Dynamic force-stop Toast behavior remains unchanged.

### Configuration Store

The module application process owns a private SharedPreferences file with:

- `execution_mode`, default `AUTO`;
- `background_optimization_enabled`, default `true`;
- a crash-consistent, versioned optimization generation describing only
  background settings changed by this module;
- a crash-consistent root-attempt journal containing the authorization epoch,
  current terminal state and epoch, current APK version, and the most recently
  consumed settings-open and SystemUI-generation trigger IDs;
- last runtime result fields needed by the settings screen.

SystemUI never reads the module data directory directly. Configuration crosses
the existing authenticated Binder boundary. The bridge returns an immutable
configuration snapshot when SystemUI registers its callback, then pushes later
changes. Until the first authenticated snapshot arrives, SystemUI remains in an
explicit `UNCONFIGURED` state and force-stop requests fail closed. It must not
substitute `AUTO`, because that could violate a persisted `Root only` or
`SystemUI only` choice. After the first valid snapshot, SystemUI retains that
last-known snapshot across transient bridge disconnects.

### Persistent Bridge Connection

The current module does not receive an Android `Context` directly in
`onPackageReady()`. Initialization therefore has two phases:

1. `onPackageReady()` installs the existing vendor-specific status-bar hook and
   prepares an idempotent runtime initializer.
2. The initializer first tries the current SystemUI `Application` from the
   process `ActivityThread`. If that is not yet available, it uses the exact
   `PhoneStatusBarView.onFinishInflate()` lifecycle method on both target
   families. Oplus already uses this method for listener attachment. Xiaomi adds
   a second interceptor on the target SystemUI status-bar class solely to pass
   the inflated view's application context to the initializer; it does not
   change touch behavior. Method resolution may walk only vendor/SystemUI
   status-bar classes and must never hook framework `View`, `ViewGroup`, or
   `FrameLayout` globally. Presence and pre-gesture invocation of this method on
   both supported devices are required evidence before implementation can be
   accepted; absence fails warmup closed and requires a design revision.
3. The first valid application context atomically wins initialization; later
   status-bar view instances do not create additional connections.
4. Classify the direct SystemUI backend without killing any package.
5. Asynchronously bind the explicit `ForceStopBridgeService` with
   `BIND_AUTO_CREATE` and `FLAG_INCLUDE_STOPPED_PACKAGES`.
6. Retain the `ServiceConnection` in the injected Xposed module instance for
   the complete SystemUI process lifetime.
7. Register a versioned SystemUI session and configuration callback after
   connection.

The bind and RootService warmup must never block SystemUI's main thread. The
current single request worker remains responsible for force-stop work.

If the bridge Binder dies, mark root disconnected and schedule one bounded
reconnect attempt. A subsequent user request may trigger another reconnect,
but the module must not run an unbounded timer or tight retry loop.

### Root Warmup

`ForceStopBridgeService.onCreate()` loads the durable root-attempt journal before
deciding whether to start an asynchronous RootService bind. It starts one only
when the current authorization epoch is not suppressed by a persisted terminal
state. Ordinary Service recreation, process death, Binder reconnect, or repeated
registration of the same SystemUI generation never advances the authorization
epoch. The service retains a live `IRootActivityController` and exposes root
state to SystemUI and the settings Activity. A connected, alive root Binder is
reused.

Root availability is proven by a successful RootService connection, not by the
presence of an `su` binary. The complete root connection state is
`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `TRANSIENT_ERROR`, `DENIED`, or
`INCOMPATIBLE`. `DENIED` is used only when libsu supplies explicit evidence that
root authorization was rejected. `INCOMPATIBLE` is used for deterministic local
failures such as a missing RootService component, null/incompatible service
Binder, or interface mismatch. Timeout, Binder death/disconnection, and unknown
connection exceptions are retryable `TRANSIENT_ERROR` and retain Debug details.

`DENIED` and `INCOMPATIBLE` are written to the durable journal before observers
are notified. A new settings-open token or genuinely new SystemUI
process-generation atomically records the consumed trigger, advances the
authorization epoch, and clears `DENIED` before permitting one attempt.
`INCOMPATIBLE` remains suppressed until the installed APK version changes;
neither user/system events nor a new SystemUI generation clear it. The journal
retains the current and immediately previous consumed trigger IDs so Activity
recreation, Binder reply loss, or Bridge process recreation cannot replay an
attempt. A new APK version initializes a new compatibility epoch without
discarding unrelated settings or optimization state.

Each root bind owns a unique in-process `attemptId`, a dedicated
`ServiceConnection`, and a ten-second deadline measured with
`SystemClock.elapsedRealtime()`. The transition to `CONNECTING`, active attempt
ID, and deadline are installed atomically before `RootService.bind()`. Success,
failure, and timeout mutate state only when their attempt ID is still active.
Timeout invalidates the attempt, calls `RootService.unbind(connection)` on the
main thread, and enters `TRANSIENT_ERROR`. A callback arriving for an invalid or
superseded attempt is ignored and immediately unbound; it cannot replace the
current controller. Disconnect after a successful connection clears the
controller and enters `DISCONNECTED`. This deadline exists independently of any
force-stop request, so warmup cannot remain `CONNECTING` forever.

The initial bind is only an authorization request, not proof that a prompt was
shown. No automatic loop occurs. The module cannot use root to grant itself root
because root is not yet available at that boundary.

The Activity calls `ensureRootConnection(openToken)` and receives only a
transport receipt (`ACCEPTED` or `DUPLICATE`), never an authorization result. It
creates a random `openToken` only for a fresh screen creation and saves both the
token and `deliveryAcknowledged` state. Rotation before Service connection may
redeliver the same unacknowledged token; the bridge's durable token check makes
that idempotent. Once either receipt is returned, the Activity marks delivery
acknowledged and ordinary resume does not call again. If RootService is already
connected or an attempt is active, an accepted token is consumed without
starting a second bind. The runtime observer remains the only source of the
displayed connection state.

### Event-driven Root Recovery

The persistent SystemUI binding remains the primary lifecycle mechanism. System
events are recovery signals only; they do not create a second keepalive system.
The injected SystemUI process registers the following listeners once after its
application context has been initialized:

1. A platform task-stack listener for foreground-task changes.
2. An application-context dynamic receiver for `ACTION_USER_PRESENT`.
3. An application-context dynamic receiver for `ACTION_SCREEN_ON`.

The task-stack listener must use the narrow platform task-stack registration
surface available to SystemUI. It does not hook `Activity.onResume()` in target
applications, request Usage Access, or add application packages to LSPosed
scope. Registration is capability-checked once per SystemUI process. A missing
API or permission rejection disables only this listener for that process,
records a Debug diagnostic, and leaves the screen/unlock recovery signals and
persistent binding operational. It must not retry listener registration on
every task change.

The two broadcasts are registered separately with the API 37 overload and
`Context.RECEIVER_EXPORTED`, because their sender is system_server rather than
the SystemUI UID. Each receiver has a filter containing exactly one platform
protected action and ignores every other action. The design relies on Android's
protected-broadcast enforcement; it does not add a custom exported action. A
`SecurityException`, `IllegalArgumentException`, or linkage failure records one
Debug diagnostic and disables only that receiver for the process. It does not
disable the other receiver, task listener, persistent binding, or gestures.

Every recovery signal calls one non-blocking state-machine entry point. It
performs no work when RootService is `CONNECTED` or `CONNECTING`. It permits one
bind when the state is `DISCONNECTED` or `TRANSIENT_ERROR`, subject to a
process-wide five-second monotonic cooldown. Terminal `DENIED` or
`INCOMPATIBLE` remains unchanged and does not create another authorization
request.
`ACTION_SCREEN_ON` is evaluated after a 750 ms delay; a following
`ACTION_USER_PRESENT` or task change is coalesced by the same cooldown.

If the bridge Binder is disconnected, a recovery signal may request the same
bounded bridge reconnection already defined for Binder death. Once the
authenticated SystemUI session is restored, it may request RootService recovery
under the state rules above. No event blocks the SystemUI main thread.

SystemUI recovery calls carry only a fixed reason enum (`TASK_FRONT`,
`USER_PRESENT`, or `SCREEN_ON`) and a monotonic cooldown-window ID. They never
carry or persist the foreground package, Activity, task ID, or label. Repeated
reason/window pairs are idempotent, and all reasons share the global cooldown.

## Typed Backend Results

Replace the boolean-only backend contract with an immutable typed result:

- `SUCCESS`;
- `PERMISSION_REJECTED`;
- `UNSUPPORTED` for a missing or incompatible API surface;
- `TRANSIENT_TRANSPORT_FAILURE` for Binder death, timeout, or temporary service
  unavailability;
- `OPERATION_FAILED` when the backend was reached but did not complete the
  requested force-stop.

Every result includes the backend and elapsed monotonic milliseconds. Exception
classification unwraps `InvocationTargetException` recursively. Only an
unwrapped `SecurityException` becomes `PERMISSION_REJECTED`. Missing reflective
classes or methods become `UNSUPPORTED`; Binder or connection failures become
`TRANSIENT_TRANSPORT_FAILURE`; other invocation failures become
`OPERATION_FAILED`. This typed contract is used by the policy state machine,
runtime reporting, diagnostics, and tests.

## Execution Modes

The settings screen offers the following exact modes:

### Automatic (default)

1. If RootService is already connected, use the root backend immediately.
2. Otherwise, if the direct SystemUI backend was classified as available, use
   it rather than waiting for root startup.
3. If SystemUI is unavailable, wait a bounded time for the already-started root
   connection.
4. A rejected SystemUI attempt fuses that backend off and retries the current
   request through root.
5. A root transport failure may fall back to SystemUI only when SystemUI is
   still classified as available.

This policy is based on readiness and capability, not a permanent assumption
that root or SystemUI is faster.

### Root only

Skip `BinderForceStopMethod` completely. Use the warm bridge/RootService path.
If root is unavailable, fail without attempting SystemUI.

### SystemUI only

Skip the root execution backend. The persistent bridge remains connected for
configuration and runtime status, and RootService remains warm as required by
the user, but it is not allowed to execute this request. If SystemUI was
classified unavailable or fused off, fail immediately.

### Root first

Use root, waiting only for the configured bounded connection timeout when it is
still connecting. Fall back to SystemUI if allowed and available.

### SystemUI first

Use SystemUI when available. Fall back to root on failure.

### Complete selection table

No mode executes while configuration is `UNCONFIGURED`. After configuration is
known, backend ordering is deterministic. A terminal Root state means `DENIED`
or `INCOMPATIBLE`; `TRANSIENT_ERROR` follows the same bounded-retry rows as
`DISCONNECTED`:

| Mode | Root state | SystemUI state | Attempt order |
| --- | --- | --- | --- |
| Automatic | `CONNECTED` | any | Root; then SystemUI only if available |
| Automatic | `CONNECTING`/`DISCONNECTED`/`TRANSIENT_ERROR` | `AVAILABLE` | SystemUI; then one bounded Root wait |
| Automatic | `CONNECTING`/`DISCONNECTED`/`TRANSIENT_ERROR` | unknown/unavailable/rejected | one bounded Root wait only |
| Automatic | terminal | `AVAILABLE` | SystemUI only; no Root retry or wait |
| Automatic | terminal | unknown/unavailable/rejected | fail immediately |
| Root only | `CONNECTED` | any | Root only |
| Root only | `CONNECTING`/`DISCONNECTED`/`TRANSIENT_ERROR` | any | one reconnect/bounded wait, then Root only |
| Root only | terminal | any | fail immediately |
| SystemUI only | any | `AVAILABLE` | SystemUI only |
| SystemUI only | any | unknown/unavailable/rejected | fail immediately |
| Root first | `CONNECTED` | any | Root; then SystemUI only if available |
| Root first | `CONNECTING`/`DISCONNECTED`/`TRANSIENT_ERROR` | any | one bounded Root wait; then SystemUI only if available |
| Root first | terminal | `AVAILABLE` | SystemUI only; no Root retry or wait |
| Root first | terminal | unknown/unavailable/rejected | fail immediately |
| SystemUI first | `CONNECTED` | `AVAILABLE` | SystemUI; then Root |
| SystemUI first | `CONNECTING`/`DISCONNECTED`/`TRANSIENT_ERROR` | `AVAILABLE` | SystemUI; then one bounded Root wait |
| SystemUI first | terminal | `AVAILABLE` | SystemUI only; no Root retry or wait |
| SystemUI first | `CONNECTED` | unknown/unavailable/rejected | Root only |
| SystemUI first | `CONNECTING`/`DISCONNECTED`/`TRANSIENT_ERROR` | unknown/unavailable/rejected | one bounded Root wait only |
| SystemUI first | terminal | unknown/unavailable/rejected | fail immediately |

A successful attempt ends the sequence. A SystemUI
`PERMISSION_REJECTED` result produces `FUSED_REJECTED`; a SystemUI
`UNSUPPORTED` result produces `UNAVAILABLE_UNSUPPORTED`. Both states skip later
SystemUI attempts for that process and allow only the documented Root fallback,
but only permission rejection is called a rejection fuse. Root-backend outcomes
never change SystemUI capability. `TRANSIENT_TRANSPORT_FAILURE` and
`OPERATION_FAILED` do not disable a backend but allow the documented fallback
for the current request. A Root command-level failure never silently changes
the persisted execution mode.

Every request records the selected mode, attempted backend sequence, result,
and elapsed time in Debug diagnostics. Release builds retain only the minimal
last-result state displayed in the Activity; they do not restore Debug log
strings.

## SystemUI Capability Classification

At SystemUI module initialization, perform one non-destructive check using the
current SystemUI process identity and package context for
`android.permission.FORCE_STOP_PACKAGES`.

- Permission denied: mark `UNAVAILABLE` for this SystemUI process. Automatic,
  Root-only, and Root-first modes never spend work on the SystemUI backend.
- Permission granted: mark `AVAILABLE`, but do not claim a successful
  force-stop until a real user request succeeds.
- First real invocation rejected with `SecurityException`, or an
  `InvocationTargetException` whose cause is a permission rejection: mark
  `FUSED_REJECTED` for the remainder of this SystemUI process and retry through
  root when the selected mode permits it.
- A missing or incompatible force-stop method marks
  `UNAVAILABLE_UNSUPPORTED` for the remainder of this SystemUI process and
  follows the same configured fallback behavior.
- Binder death or another transient transport error does not permanently fuse
  the backend. It fails the current attempt and follows the configured fallback
  order.

Classification is repeated only when SystemUI starts a new process. The
settings Activity displays the current process result received over Binder.

## Background Optimization

The `Background optimization` switch defaults to enabled and is independent of
the always-on root warm connection.

After root connects, enabling the switch applies only these generic Android
settings to `com.wayne.statusbarforceclose`:

- add the package to the device-idle power-save whitelist if it is not already
  present;
- set UID AppOp `RUN_IN_BACKGROUND` to `MODE_ALLOWED` when the operation exists;
- set UID AppOp `RUN_ANY_IN_BACKGROUND` to `MODE_ALLOWED` when the operation
  exists.

Use structured Android service/Binder APIs from the root process rather than
parsing localized `cmd` output. The device-idle adapter uses the
`IDeviceIdleController` service to query, add, and remove the package. The
AppOps adapter resolves operation names with `AppOpsManager`, verifies the
installed package UID for its Android user, reads the raw UID override through
`IAppOpsService.getUidOps`, and changes it through `setUidMode`. An effective
package mode must not be mistaken for the raw UID override being changed.
Missing operations or incompatible hidden methods are independently reported as
unsupported; there is no localized shell-output fallback.

The bridge application process serializes optimization work and owns the durable
state. The root controller exposes structured query and single-item mutation
operations; it never writes SharedPreferences itself.

Before the first modification of each enablement generation, query and persist
a versioned snapshot:

- whether the package was already in the device-idle whitelist;
- the exact raw mode for each supported AppOp;
- the exact value the module intends to apply;
- whether this module actually changed each item.

Each generation uses this durably committed state machine:

```text
IDLE -> CAPTURED -> APPLY_PENDING(item) -> ACTIVE
ACTIVE -> RESTORE_PENDING(item) -> RESTORED -> IDLE(new generation allowed)
```

The bridge commits `APPLY_PENDING` before each root mutation, performs the
mutation, queries the resulting state, then commits the item outcome. On process
restart it reconciles a pending item by comparing current, original, and applied
values before retrying or advancing. Reapplying an active generation never
overwrites its original snapshot. After every item is completely restored, the
generation becomes `RESTORED`; only then may a later enable capture a new
baseline and generation ID.

Disabling the switch restores with ownership checks:

- removes the device-idle whitelist entry only if this module added it;
- restores each AppOp to its exact captured raw mode only if this module changed
  it;
- before restoring any item, verifies that its current value still equals the
  exact value applied by this module;
- if current state drifted, leaves the newer user or vendor value untouched and
  records terminal item state `SKIPPED_OWNERSHIP_CONFLICT`, relinquishes
  ownership, and never retries that item automatically;
- retains enough state to retry an individually failed restoration.

`RESTORE_PENDING` is committed before mutation. After mutation, the bridge
queries the actual value before marking the item restored. Tests inject process
death at every pending transition. `RESTORED` and
`SKIPPED_OWNERSHIP_CONFLICT` both count as resolved for generation closure;
transport or mutation failures remain retryable. Conflict diagnostics retain
the original, applied, and observed values, but a later enable starts a new
generation and captures the then-current value instead of reusing the obsolete
baseline.

No arbitrary Android permission grants are attempted. Normal permissions that
do not improve this lifecycle are not declared, and privileged signature
permissions are not forced onto the package. The Activity provides system
settings links for vendor-specific autostart and background protection instead
of guessing private AppOps.

## Binder Contract and Security

Extend the bridge contract with narrowly scoped operations for:

- force-stop through root;
- register/unregister a versioned SystemUI session and configuration callback;
- read the current runtime/configuration snapshot;
- report SystemUI capability and typed execution results to the bridge;
- register/unregister a settings-Activity runtime observer;
- ensure one root connection attempt for a fresh settings-screen open token;
- request event-driven root recovery from an authenticated SystemUI session;
- apply or restore this package's background optimization.

Every registration includes a protocol version. A SystemUI session includes a
random process-generation ID and monotonically increasing report revision.
Configuration snapshots and callbacks include their own monotonically
increasing revision. The bridge ignores stale or out-of-order reports and
callbacks. Protocol mismatch returns `INCOMPATIBLE`, performs no mutation or
force-stop, and is displayed as requiring a SystemUI restart after an APK
upgrade.

Successful registration returns an opaque session token bound to calling UID,
process-generation ID, callback Binder, and protocol version. Every
SystemUI-originated force-stop, configuration read, and runtime report carries
the protocol version, generation ID, and session token. The bridge requires a
matching live session before processing it. Callback Binder death invalidates
the token. The AIDL change replaces the old unversioned
`forceStop(String, int)` transaction with the session-qualified operation; no
legacy transaction remains that can reach root. An injected 0.2.0 SystemUI
client left alive after APK replacement therefore fails closed until SystemUI
restarts and registers using the new protocol.

The SystemUI report contains no target package or label. It contains only the
process generation, revision, capability state, selected mode, attempted backend
sequence, typed result, and elapsed time. The bridge persists only this minimal
last-result state for the settings screen.

Authorization rules:

- Force-stop is accepted only from a UID whose packages include
  `com.android.systemui` and whose live session token, process generation, and
  protocol version match, strengthening the current policy.
- Settings and optimization calls are accepted only from the module's own UID.
- A fresh-screen root connection command is accepted only from the module's own
  UID and is idempotent for a repeated open token.
- Event-driven root recovery is accepted only from the live authenticated
  SystemUI session. Its protocol version, process generation, and opaque session
  token must match; its reason enum and monotonic window ID are validated.
- SystemUI may read execution configuration and register its callback, but may
  not request optimization changes. Only authenticated SystemUI may submit a
  SystemUI runtime report; configuration reads and reports require the same live
  compatible session as force-stop.
- The root controller never accepts an arbitrary package for background
  optimization; its target is hard-coded to this module package and verified
  UID.
- Invalid modes, negative users, blank target packages, stale callbacks, and
  unknown protocol versions fail closed.

The settings Activity binds `ForceStopBridgeService` with `BIND_AUTO_CREATE` in
`onStart()` and unbinds in `onStop()`. `onCreate(savedInstanceState == null)`
creates a fresh open token and records a pending one-shot root request, which is
delivered after that Activity binds. Saved state retains the token and its
delivery-acknowledged bit. A restored Activity redelivers only the same token
when delivery was not acknowledged; it never creates a new token. `onStart()` by
itself never creates a new authorization attempt. The Activity registers a
runtime observer for asynchronous root, SystemUI-session, optimization, and
last-result changes; Activity recreation replaces the observer safely. Observer
Binder death and SystemUI callback death are tracked separately. The UI reports
SystemUI connected only while a live, authenticated SystemUI session callback
exists, not merely because the bridge Service process exists.

## Failure Handling

- Settings Activity opens even when LSPosed is disabled or root is denied.
- A disconnected SystemUI shows `Not connected`; it is not reported as proof
  that the hook failed until SystemUI has had a chance to bind.
- Root denial does not trigger repeated authorization prompts. Only a fresh
  settings-screen creation without restored state or registration of a
  genuinely new SystemUI process generation starts one additional attempt.
- Task changes, screen-on, and user-present events recover only disconnected or
  transiently failed connections and share one five-second cooldown. They never
  reset terminal `DENIED`/`INCOMPATIBLE` state.
- Configuration callback failure removes the stale callback without affecting
  status-bar input.
- Background optimization applies and restores each item independently and
  displays partial status rather than claiming all-or-nothing success.
- Force-stop still shows the dynamic success Toast only after a successful
  backend result. Debug builds log a distinct failure reason; the settings page
  records the last failed backend without exposing target-app history.

## Packaging Changes

The APK will intentionally change from the 0.2.0 packaging contract:

- add exactly one exported launcher Activity;
- add an adaptive launcher icon and settings resources;
- retain exactly one exported bridge Service;
- retain no Provider, manifest Receiver, native library, foreground service, or
  Android permission unless implementation proves one indispensable and the
  design is revised first; the screen/unlock receiver is registered dynamically
  from the existing SystemUI application context;
- retain API 102 metadata and static scope `com.android.systemui`.

Release validation and README statements must be updated to match the new
Activity and icon rather than continuing to claim that the APK has no UI.

## Test Strategy

### Unit tests

- All five mode/backend-order combinations.
- Automatic selection with root connected, root connecting, SystemUI available,
  and SystemUI unavailable.
- One-time SystemUI capability classification.
- `FUSED_REJECTED` only for SystemUI permission rejection and the distinct
  `UNAVAILABLE_UNSUPPORTED` state for missing SystemUI APIs.
- No fuse for transient Binder failure.
- Current-request root fallback after rejection.
- Configuration snapshot validation and callback replacement/removal.
- Caller authorization for force-stop, configuration, fresh-screen root
  connection, and optimization; repeated open-token idempotency.
- Durable root authorization epochs across Bridge process death: repeated
  SystemUI generation/open token suppression, atomic terminal-state recording,
  `DENIED` clearing only on an eligible new trigger, and `INCOMPATIBLE` clearing
  only on APK-version change.
- Ten-second bind deadline, attempt-ID arbitration, timeout unbind, late/stale
  callback rejection, disconnect transition, and terminal-versus-transient
  error classification.
- Background snapshot captured once, idempotent reapply, exact restore, partial
  support, and partial restore retry.
- Root denial does not create an automatic retry loop.
- Task-stack registration capability handling, a single registration per
  SystemUI process, recovery-state eligibility, five-second cross-event
  coalescing, delayed screen-on handling, and terminal-state suppression.
- One root request for a fresh settings-screen creation, rotation/restoration/
  resume without a second authorization attempt, bind-before-rotation delivery,
  same-token redelivery after rotation before acknowledgment, and a new request
  after closing and freshly reopening following authorization changes.
- `UNCONFIGURED` fails closed and a last-known configuration survives bridge
  disconnect.
- Typed exception/result classification and every row in the complete selection
  table.
- Process-generation registration, one retry per new generation, protocol
  mismatch, stale/missing session token, legacy unversioned call rejection, and
  out-of-order revision rejection.
- Optimization generations, ownership drift, repeated enable/restore cycles,
  terminal ownership-conflict closure, and process death at each apply/restore
  pending transition.

Policy, protocol sequencing, optimization generation, and result
classification are extracted into pure-Java state machines with fake Android
adapters so host JUnit covers deterministic behavior.

### Instrumentation tests

- Activity `onStart`/`onStop`, recreation, observer replacement, and status
  updates.
- Bridge binding, callback Binder death, SystemUI-session death, and protocol
  mismatch.
- Task-front, screen-on, and user-present delivery without foreground-app data;
  task-stack registration failure leaves the other recovery paths active.
- Separate API 37 `RECEIVER_EXPORTED` registrations, exact-action filtering,
  per-receiver registration failure isolation, and rejection/non-delivery of a
  non-system attempt to forge the protected recovery broadcasts.
- SharedPreferences durable transitions and restart reconciliation.
- Root-side device-idle and AppOps adapter queries on a disposable test package
  or isolated test device; tests restore their exact captured state.
- Merged launcher intent and exported-component behavior.

### Build and static verification

- Run unit tests, lint, Debug build, and Release build.
- Inspect the merged manifest for one launcher Activity and one bridge Service,
  with no Provider, manifest Receiver, foreground service, or permissions.
- Verify API 102 metadata, SystemUI scope, `assets/main.jar`, R8 retention of the
  required Activity/Service/root classes, and removal of Debug-only diagnostics
  from Release.

### Device verification

Use the same Debug APK on both daily devices and keep device evidence separate:

- Xiaomi 17 Ultra for Leica / HyperOS 4 / Android 17.
- OnePlus 13T (`PKX110`) / ColorOS 16 / Android 16.

For each device verify:

1. Activity launches and reports root/SystemUI connection accurately.
2. SystemUI restart creates one persistent bridge connection and one root bind.
3. Repeated gestures reuse the same root process/Binder without cold startup.
4. Automatic mode follows root readiness and the one-time SystemUI capability.
5. Root-only skips SystemUI; SystemUI-only skips root execution; both priority
   modes use the documented fallback order.
6. Real user ID and protected-target behavior remain unchanged.
7. The success Toast still contains the actual target label.
8. Enabling background optimization records and applies supported changes.
9. Disabling restores the exact captured state and does not remove a preexisting
   user whitelist.
10. After an induced RootService Binder loss, one foreground-task change
    restores the connection before the next gesture without a repeated bind.
11. Screen-on followed by user-present/task-front within the cooldown produces
    at most one recovery attempt; terminal root denial produces none.
12. Task-stack listener registration succeeds and emits Debug evidence on each
    supported device; if capability is absent, failure is isolated and the
    persistent binding plus screen/unlock recovery paths remain functional.
13. SystemUI, `system_server`, LSPosed, the module process, and root service stay
    stable across repeated use.

Device installation, root changes, SystemUI restart, or reboot require a later
explicit execution step; none are part of approving this design document.

## Completion Criteria

The feature is complete only after:

- all automated and static checks pass;
- settings and bridge state agree across process restarts;
- root reuse is demonstrated by stable process/Binder evidence rather than one
  fast observation;
- event-driven recovery is demonstrated after a real Binder loss, with
  cross-event cooldown and terminal-state suppression proven in logs;
- SystemUI capability is measured once per process and ColorOS avoids repeated
  unsupported Binder attempts;
- background optimization apply and exact restore are verified;
- all five modes are validated according to their documented backend order;
- both supported devices retain the previously verified double-tap, user-ID,
  force-stop, and Toast behavior;
- README, release validation, release notes, source Release, official Xposed
  Release, and LSPosed store metadata describe the new UI and lifecycle
  accurately.
