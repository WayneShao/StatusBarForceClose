# Settings And Root Warmup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native settings screen, five selectable force-stop policies, a persistent authenticated SystemUI-to-Bridge-to-libsu connection, event-driven root recovery, and reversible module-owned background optimization.

**Architecture:** Pure Java state machines own execution selection, root-attempt epochs, recovery cooldown, SystemUI sessions, and optimization ownership. Android adapters handle AIDL, libsu, hidden platform Binder calls, SystemUI lifecycle events, persistence, and the native Activity; every adapter fails closed and Debug builds emit structured evidence.

**Tech Stack:** Java 17, Android SDK 37/minSdk 36, libxposed API 102, libsu 6.0.0, HiddenApiBypass 6.1, AIDL, native Android Views/resources, JUnit 4, Gradle/AGP 9.2.1.

---

## File Map

- `ExecutionMode`, `BackendKind`, `BackendStatus`, `BackendResult`, `ExecutionPlan`, `ForceStopPolicy`: typed pure-Java backend selection.
- `RootConnectionState`, `RootAttemptStateMachine`, `RecoveryGate`: pure root lifecycle, deadlines, terminal suppression, and event cooldown.
- `SystemUiCapability`, `SystemUiCapabilityClassifier`, `SystemUiSessionRegistry`: one-process capability and authenticated session policy.
- `OptimizationItem`, `OptimizationJournal`, `OptimizationStateMachine`: crash-consistent ownership and restoration decisions.
- `BridgeStateStore`: the only SharedPreferences serializer for configuration, root journal, optimization journal, and minimal runtime state.
- `RootOperations` and `OptimizationOperations`: stable Bridge ports; unavailable implementations keep early protocol integration compilable until real adapters are wired.
- `RootConnectionManager`: libsu adapter with per-attempt `ServiceConnection`, deadline, stale callback rejection, and observer dispatch.
- `BackgroundOptimizationController`: Bridge orchestration; `RootSystemSettingsAdapter`: root-side structured device-idle/AppOps Binder implementation.
- `ForceStopBridgeService` and AIDL parcelables/callbacks: versioned process boundary.
- `SystemUiRuntime`: persistent bind, configuration callback, recovery events, and typed force-stop execution.
- `TaskStackListenerBridge`: narrow hidden platform listener adapter backed by a local compile-only Android stub module.
- `SettingsActivity`: native settings UI; resources contain English and Simplified Chinese labels and an adaptive icon.

### Task 1: Establish Android Instrumentation Infrastructure

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/wayne/statusbarforceclose/InstrumentationSmokeTest.java`

- [ ] Configure `androidx.test.runner.AndroidJUnitRunner` 1.7.0 and AndroidX Test JUnit 1.3.0 as `androidTestImplementation` only; these dependencies stay in the test APK and add no production APK runtime surface.
- [ ] Add a minimal smoke test that asserts the instrumentation target package is `com.wayne.statusbarforceclose`.
- [ ] Run `./gradlew assembleDebugAndroidTest --no-daemon`, verify both app and test APKs exist, and inspect the test manifest runner/target package.
- [ ] Commit `test: establish Android instrumentation runner`.

### Task 2: Add Typed Execution Policy

**Files:**
- Create: `app/src/test/java/com/wayne/statusbarforceclose/ForceStopPolicyTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/BackendResultTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/ExecutionMode.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/BackendKind.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/BackendStatus.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/BackendResult.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/ExecutionPlan.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopPolicy.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopMethod.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopCoordinator.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopResult.java`
- Modify: `app/src/test/java/com/wayne/statusbarforceclose/ForceStopCoordinatorTest.java`

- [ ] Write failing tests for every row of the five-mode selection table, success short-circuiting, permitted fallback, terminal root state, fused SystemUI rejection, unsupported API, and transient failure.
- [ ] Run `./gradlew testDebugUnitTest --tests '*ForceStopPolicyTest' --tests '*ForceStopCoordinatorTest' --no-daemon` and verify RED from missing typed policy.
- [ ] Implement immutable typed results and minimal deterministic selection/execution without Android dependencies.
- [ ] Run the focused command and verify GREEN; run all unit tests and commit `feat: add typed force-stop execution policy`.

### Task 3: Add Root And Recovery State Machines

**Files:**
- Create: `app/src/test/java/com/wayne/statusbarforceclose/RootAttemptStateMachineTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/RecoveryGateTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootConnectionState.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootAttemptStateMachine.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RecoveryReason.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RecoveryGate.java`

- [ ] Write failing tests for initial warmup, ten-second deadline, stale attempt/callback rejection, connected reuse, disconnect, timeout as `TRANSIENT_ERROR`, no retry while connecting, five-second cross-event cooldown, and terminal suppression.
- [ ] Write failing tests proving a new settings token/new SystemUI generation clears only `DENIED`, while APK-version change alone clears `INCOMPATIBLE`.
- [ ] Run both focused tests and verify RED.
- [ ] Implement the pure state machines using injected monotonic timestamps and explicit attempt IDs; do not classify a missing libsu callback as `DENIED`.
- [ ] Run focused/all tests and commit `feat: define root connection and recovery states`.

### Task 4: Add Durable Stores And SystemUI Sessions

**Files:**
- Create: `app/src/test/java/com/wayne/statusbarforceclose/RootAttemptJournalTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/SystemUiSessionRegistryTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/ConfigurationStateTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/BridgeStateCodecTest.java`
- Create: `app/src/androidTest/java/com/wayne/statusbarforceclose/BridgeStateStoreInstrumentedTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootAttemptJournal.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/SystemUiSessionRegistry.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopConfiguration.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/BridgeStateStore.java`

- [ ] Write RED model tests for atomic epoch advancement, duplicate open/generation suppression after recreation, terminal persistence, protocol mismatch, callback death/token invalidation, revision ordering, and `UNCONFIGURED` fail-closed behavior.
- [ ] Write RED codec tests for every persisted field, unknown/corrupt schema fail-closed behavior, APK compatibility epochs, and crash-boundary journal snapshots; then write an androidTest that recreates the real SharedPreferences adapter and verifies synchronous persistence.
- [ ] Implement immutable journal/configuration models and a SharedPreferences adapter that uses synchronous `commit()` for generation transitions.
- [ ] Never persist target package, Activity, task ID, label, or foreground history.
- [ ] Run focused/all host tests and `assembleDebugAndroidTest`; verify the instrumented test first fails to compile before the store exists and compiles after implementation. Commit `feat: persist bridge configuration and sessions`.

### Task 5: Specify The Versioned Binder Boundary With Failing Tests

**Files:**
- Create: `app/src/androidTest/java/com/wayne/statusbarforceclose/BridgeProtocolInstrumentedTest.java`
- Modify: `app/src/test/java/com/wayne/statusbarforceclose/RootBridgeCallerPolicyTest.java`

- [ ] Write RED caller/session tests for module UID, SystemUI UID/package, wrong protocol, stale generation/token, dead callback, invalid user/mode/reason, and removal of the legacy unversioned force-stop path.
- [ ] Add a test-local wished-for new-protocol Stub with a counting fake and raw-Parcel cases for the legacy payload, wrong protocol/token/generation and zero backend calls.
- [ ] Add the same raw legacy/invalid-session cases against a bound wished-for `ForceStopBridgeService` API before changing the production AIDL or Service.
- [ ] Run focused host tests and `assembleDebugAndroidTest`; record RED from the missing new protocol/Service methods. Keep these failing tests uncommitted until Task 6 makes them GREEN.

### Task 6: Integrate The Bridge Binder Server

**Files:**
- Modify: `app/src/main/aidl/com/wayne/statusbarforceclose/IForceStopBridge.aidl`
- Modify: `app/src/main/aidl/com/wayne/statusbarforceclose/IRootActivityController.aidl`
- Create: `app/src/main/aidl/com/wayne/statusbarforceclose/ISystemUiCallback.aidl`
- Create: `app/src/main/aidl/com/wayne/statusbarforceclose/IRuntimeObserver.aidl`
- Create: parcelable AIDL declarations and Java parcelables under `app/src/main/{aidl,java}/com/wayne/statusbarforceclose/`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/RootBridgeCallerPolicy.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/BridgeRequestDispatcherTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/ConfigurationDeliveryTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/BridgeRequestDispatcher.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/BridgeObserverRegistry.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootOperations.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/OptimizationOperations.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopBridgeService.java`
- Extend: `app/src/androidTest/java/com/wayne/statusbarforceclose/BridgeProtocolInstrumentedTest.java`

- [ ] Write RED dispatcher tests for every Binder operation: SystemUI session register/unregister, config snapshot/read, revisioned callback push, typed force-stop, runtime result report, settings observer register/unregister, fresh-open root receipt, event recovery, and optimization enable/restore.
- [ ] Write RED authorization tests per operation for module UID versus authenticated SystemUI UID, protocol/generation/token mismatch, callback Binder death, stale/out-of-order revisions, invalid payloads, and observer fan-out cleanup.
- [ ] Write the full RED chain `Activity mode write -> durable commit -> revision increment -> callback -> SystemUI atomic replacement`, including stale callback rejection and last-known config retention after disconnect.
- [ ] Define stable `RootOperations` ports for typed force-stop/fresh-open/recovery/state and `OptimizationOperations` ports for snapshot/apply/restore. Implement unavailable defaults so Bridge integration compiles before Tasks 7 and 8 wire real adapters.
- [ ] Define protocol constant `1`, opaque session registration, immutable configuration/runtime snapshots, typed execution results, settings root receipt, recovery call, optimization call, observer registration, and required protocol/generation/session fields on every SystemUI operation.
- [ ] Implement the Binder Stub as a thin caller-identity adapter over `BridgeRequestDispatcher`; do not place policy transitions directly in generated-AIDL methods.
- [ ] Persist only the minimal last backend/result/elapsed state, never target-app history. Replace the old unversioned transaction completely and prove raw legacy Parcel input cannot call the fake root backend.
- [ ] Run the Task 5 real-service raw Parcel and invalid-session tests after implementation; compile AIDL/androidTest, inspect generated transaction surface, run all host tests GREEN, and commit tests plus production as `feat: integrate authenticated bridge service`.

### Task 7: Implement Libsu Warm Connection And Root Operations

**Files:**
- Create: `app/src/test/java/com/wayne/statusbarforceclose/RootConnectionManagerTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootConnectionManager.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootBindAdapter.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootOperationsAdapter.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/RootOperationsWiringTest.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopBridgeService.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/RootActivityManagerService.java`
- Remove after replacement: `app/src/main/java/com/wayne/statusbarforceclose/RootBridgeForceStopMethod.java`
- Modify: `app/proguard-rules.pro`

- [ ] Write RED tests with a fake bind adapter for one bind per attempt, ten-second timeout, main-thread unbind, stale/late callback disposal, alive Binder reuse, Binder death, process recreation under persisted terminal state, and no automatic loop.
- [ ] Implement `RootConnectionManager` around dedicated per-attempt `ServiceConnection` objects and `RootService.bind/unbind`; all callbacks are gated by attempt ID.
- [ ] Start warmup in Bridge `onCreate()` only when the durable journal permits it; expose state asynchronously to callbacks.
- [ ] Write a RED composition test proving the Bridge dispatcher no longer uses unavailable root operations, then wire one `RootOperationsAdapter` instance into `ForceStopBridgeService` for force-stop, fresh-open and event recovery.
- [ ] Make root force-stop return typed results and retain actual `userId`; keep root controller targets validated.
- [ ] Run focused/all tests, compile Debug, and commit `feat: keep libsu root service warm`.

### Task 8: Add Reversible Background Optimization

**Files:**
- Create: `app/src/test/java/com/wayne/statusbarforceclose/OptimizationStateMachineTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/BackgroundOptimizationControllerTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/OptimizationOperationsWiringTest.java`
- Create: `app/src/androidTest/java/com/wayne/statusbarforceclose/RootSystemSettingsAdapterInstrumentedTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/OptimizationItem.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/OptimizationJournal.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/OptimizationStateMachine.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/BackgroundOptimizationController.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RootSystemSettingsAdapter.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/BridgeStateStore.java`
- Modify: `app/src/test/java/com/wayne/statusbarforceclose/BridgeStateCodecTest.java`
- Modify: `app/src/androidTest/java/com/wayne/statusbarforceclose/BridgeStateStoreInstrumentedTest.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/BridgeRequestDispatcher.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/ForceStopBridgeService.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/RootActivityManagerService.java`
- Modify: `app/src/main/aidl/com/wayne/statusbarforceclose/IRootActivityController.aidl`

- [ ] Write RED tests for default-enabled apply, exact original capture, idempotent reapply, preexisting whitelist preservation, partial failure/retry, exact restoration, ownership drift conflict, terminal conflict closure, and process death at every pending transition.
- [ ] Extend the store codec and real-SharedPreferences RED tests with versioned optimization journal schema, corrupt/unknown input, every pending transition and process reconstruction before implementing persistence. `BridgeStateStore` remains the only serializer.
- [ ] Before the adapter exists, add an instrumentation suite targeting only the module package. Gate every mutating test with `Assume.assumeTrue` on instrumentation argument `runRootSettingsMutation=true`, so the ordinary suite skips it. The gated suite captures raw/effective AppOps and whitelist state, verifies unsupported-method isolation, partial failure, preexisting whitelist, ownership drift and exact restore in `finally`; compile it RED then GREEN, but defer execution until Task 13. Do not add a production or test-only arbitrary-package root entry point.
- [ ] Implement structured hidden Binder adapters for `IDeviceIdleController` and `IAppOpsService`; resolve `RUN_IN_BACKGROUND` and `RUN_ANY_IN_BACKGROUND` by platform op string/code APIs and never parse shell output.
- [ ] Hard-code the optimization target to `com.wayne.statusbarforceclose` and resolve its real UID in the root process.
- [ ] Persist each transition before mutation and observed result after mutation; restore only values changed and still owned by this module.
- [ ] Write a RED composition test proving the Bridge dispatcher no longer uses unavailable optimization operations, then wire the real controller through `ForceStopBridgeService` and verify Activity enable/restore calls reach it.
- [ ] Run focused/all host tests plus `assembleDebugAndroidTest` and commit `feat: manage reversible background optimization`.

### Task 9: Add Persistent SystemUI Runtime And Capability Policy

**Files:**
- Create: `app/src/test/java/com/wayne/statusbarforceclose/SystemUiCapabilityClassifierTest.java`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/SystemUiRuntimePolicyTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/SystemUiCapability.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/SystemUiCapabilityClassifier.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/SystemUiRuntime.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/BinderForceStopMethod.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/StatusBarForceCloseModule.java`

- [ ] Write RED tests for non-destructive `FORCE_STOP_PACKAGES` permission classification, missing API, one classification per process, permission-rejection fuse, transient no-fuse, last-known config retention, and persistent connection/reconnect transitions.
- [ ] Initialize once from current SystemUI `Application` or exact target `PhoneStatusBarView.onFinishInflate()`; never hook global framework Views.
- [ ] Bind Bridge with `BIND_AUTO_CREATE` and `FLAG_INCLUDE_STOPPED_PACKAGES`, retain the connection for SystemUI lifetime, register a versioned session/callback, and execute all five modes through typed backends.
- [ ] Keep gesture processing non-blocking and retain real-user/protected-target/dynamic-Toast behavior.
- [ ] Run focused/all tests and commit `feat: add persistent SystemUI bridge runtime`.

### Task 10: Add Task, Screen, And Unlock Recovery Events

**Files:**
- Create: `hidden-api-stubs/build.gradle.kts`
- Create: `hidden-api-stubs/src/main/AndroidManifest.xml`
- Create: `hidden-api-stubs/src/main/java/android/app/TaskStackListener.java`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/proguard-rules.pro`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/RecoveryEventControllerTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/TaskStackListenerBridge.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/RecoveryEventController.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/SystemUiRuntime.java`
- Create: `app/src/androidTest/java/com/wayne/statusbarforceclose/RecoveryEventsInstrumentedTest.java`

- [ ] Add only the compile-only hidden API stub module and a static packaging assertion; do not add the production listener adapter yet. Verify the stub is absent from the app runtime classpath and APK.
- [ ] Write and run RED host tests for one listener registration, task-front delivery without task data, five-second coalescing, 750 ms screen delay, exact action filtering, separate API 37 `RECEIVER_EXPORTED` registration failures, and terminal suppression.
- [ ] Add RED instrumentation coverage for actual dynamic receiver registration flags/action delivery and task-listener construction before implementing the production event controller.
- [ ] Register the platform listener once; register `SCREEN_ON` and `USER_PRESENT` separately on application context; isolate all registration failures and log them only in Debug.
- [ ] Authenticate every recovery call with protocol/generation/session token and transmit only reason/window ID.
- [ ] Keep the callback subclass/override names in Release, then run all host tests, `assembleDebugAndroidTest`, Debug/Release builds, APK class inspection, and later Task 13 device callback delivery. Commit `feat: recover root connection from system events`.

### Task 11: Build The Native Settings Activity

**Files:**
- Create: `app/src/main/java/com/wayne/statusbarforceclose/SettingsActivity.java`
- Create: `app/src/main/res/layout/activity_settings.xml`
- Create: `app/src/main/res/values/{strings.xml,colors.xml,styles.xml,themes.xml}`
- Create: `app/src/main/res/values-zh-rCN/strings.xml`
- Create: adaptive icon/vector resources under `app/src/main/res/mipmap-anydpi-v26` and `drawable`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/proguard-rules.pro`
- Create: `app/src/test/java/com/wayne/statusbarforceclose/SettingsActivityControllerTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/SettingsActivityController.java`
- Create: `app/src/androidTest/java/com/wayne/statusbarforceclose/SettingsActivityTest.java`

- [ ] Write RED host controller tests for fresh token, bind-before-rotation same-token redelivery, delivery acknowledgment, `onStart/onStop` bind/unbind, observer replacement, three Root UI states, five-mode/config revision writes, default optimization enable, and settings-intent fallback selection.
- [ ] Before creating the Activity, write the instrumentation tests for Activity recreation, actual view state/dialog/switch interactions, Bridge observer replacement, and application/battery/vendor intent fallback; run `assembleDebugAndroidTest` and observe the expected RED compile failure because `SettingsActivity` is absent.
- [ ] Implement the minimal controller and one exported launcher Activity with adaptive icon and no new Android permission, Provider, manifest Receiver, foreground service, or boot receiver.
- [ ] Implement quiet native status rows, five-mode single-choice dialog, default-enabled optimization switch, and application/battery/vendor settings links with fallback.
- [ ] Bind/observe Bridge in `onStart/onStop`; fresh `onCreate` creates one token; saved state preserves token and delivery acknowledgment; UI shows only Connecting/Connected/Not connected and never adds authorization-result UI.
- [ ] Add English and Simplified Chinese resources; keep text inside compact rows and avoid nested cards/tutorial copy.
- [ ] Run focused/all host tests, compile instrumentation tests GREEN, run lint/Debug build, inspect merged manifest, and commit `feat: add settings activity`.

### Task 12: Update Validation, Documentation, And Run The Local Gate

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`
- Modify: `docs/release-validation.md`
- Modify: `docs/device-evidence.md` only after device validation
- Modify: `.github/ISSUE_TEMPLATE/bug_report.yml` if runtime fields change

- [ ] Update ordinary CI to run unit/lint/Debug plus `assembleDebugAndroidTest` and validate merged manifest, generated AIDL compilation, one launcher Activity/one bridge Service, no Provider/manifest Receiver/permissions, `assets/main.jar`, API 102 metadata, and hidden-stub runtime absence on every PR.
- [ ] Update release validation from “no icon/Activity” to exactly one launcher Activity, one bridge Service, icon present, no Provider/manifest Receiver/permissions/native libraries, API 102 metadata, hidden stub absence, and Release callback/R8 retention.
- [ ] Update README architecture, UI, modes, warm connection, event recovery, root authorization behavior, optimization ownership, logs, and current validation status without claiming unperformed device evidence.
- [ ] Run `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --no-daemon` with JDK 17-compatible toolchain and SDK 37.
- [ ] Parse JUnit XML for counts; inspect merged manifest and both APKs; verify package/version/SDK/components/permissions/icon, `assets/main.jar`, API 102 files, no native libraries/legacy entry/hidden stub, Debug diagnostics present, and Release diagnostics stripped.
- [ ] Review the complete diff against the approved spec and commit `docs: document settings and root warmup`.

### Task 13: Device Validation After Local Gate

**Files:**
- Modify later: `docs/device-evidence.md`

- [ ] Re-enumerate ADB and bind every command to freshly verified Xiaomi/OnePlus serial and model; never use remembered endpoints.
- [ ] Build/install the same Debug APK on one device at a time, request root through Activity startup, and restart only that device's SystemUI after explicit user authorization.
- [ ] Run the ordinary `connectedDebugAndroidTest` separately against each explicit serial with no mutation argument. Include real SharedPreferences recreation/corruption recovery, Activity lifecycle/UI, raw legacy Binder transaction rejection, authenticated protocol failures, protected broadcast delivery, and task-stack callback tests; verify the gated root-settings class is reported skipped.
- [ ] Capture this module's Doze whitelist and both raw/effective AppOps values from the host, then run only `RootSystemSettingsAdapterInstrumentedTest` with `-Pandroid.testInstrumentationRunnerArguments.class=com.wayne.statusbarforceclose.RootSystemSettingsAdapterInstrumentedTest -Pandroid.testInstrumentationRunnerArguments.runRootSettingsMutation=true`. Verify unsupported-method isolation, partial apply, preexisting whitelist, ownership drift and exact per-item restoration; after its `finally`, perform an independent host fresh-read and compare every value with the external snapshot.
- [ ] Verify hook strategy, persistent Bridge/root Binder reuse, all five modes, dynamic Toast, real user IDs, task-front/screen/unlock recovery after an induced Binder loss, terminal suppression, background optimization apply/restore, and SystemUI stability with structured logs.
- [ ] Keep device evidence separate, record only observed behavior, and do not publish/tag/release until both devices and the user accept the result.

### Task 14: Publish Only After Explicit User Approval

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `.github/release-notes/VERSION_NAME.md`
- Modify: `docs/device-evidence.md`
- Modify as observed: `README.md`, `SUMMARY`
- Official distribution repository: metadata/release assets only; never copy source code

- [ ] After both-device acceptance and a new explicit user instruction, choose the next monotonically increasing `versionCode` and semantic `versionName`, update release notes and evidence, and rerun the complete local gate.
- [ ] Commit/push the reviewed source branch, require ordinary CI success, create/push the exact `versionCode-versionName` tag, and require the signed Release workflow to succeed.
- [ ] Independently download the source Release APK/checksum, verify signature certificate, hash, package/version/SDK, components, API 102 metadata, scope, diagnostics stripping, icon/Activity contract and absence of hidden stubs/native libraries.
- [ ] Mirror the identical tag, complete notes, signed APK bytes and checksum into `Xposed-Modules-Repo/com.wayne.statusbarforceclose`; keep its default branch metadata-only and source-free.
- [ ] Compare both repositories' filenames, sizes, SHA-256 and signing certificate, then verify LSPosed module-store metadata/indexing and CDN artifact independently before calling the release complete.
