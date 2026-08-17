# Foreground Task User And Policy Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Force-stop the actual foreground Android user, including HyperOS dual-app user 999, and allow Settings and this module to be closed.

**Architecture:** Keep foreground selection in `TopTaskResolver`, isolate hidden task-field access in a reflection-only `TaskUserIdResolver`, and pass the resulting user ID through the existing coordinator unchanged. Missing or invalid task user data fails closed before either force-stop method runs.

**Tech Stack:** Java 17, Android SDK 37, libxposed API 102, JUnit 4, Gradle Android plugin, GitHub Actions.

---

### Task 1: Update Package Policy

**Files:**
- Modify: `app/src/test/java/com/wayne/statusbarforceclose/TopTaskPolicyTest.java`
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/TopTaskPolicy.java`

- [ ] **Step 1: Write failing policy tests**

Add assertions that `com.android.settings` and
`com.wayne.statusbarforceclose` are permitted while existing core and input
method protections remain intact.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.wayne.statusbarforceclose.TopTaskPolicyTest --offline --no-daemon`

Expected: FAIL because the module package is still protected.

- [ ] **Step 3: Implement the minimal policy change**

Remove the module package from `PROTECTED_PACKAGES`; do not expand or otherwise
refactor policy behavior.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused command and expect all policy tests to pass.

### Task 2: Resolve The Actual Task User

**Files:**
- Create: `app/src/test/java/com/wayne/statusbarforceclose/TaskUserIdResolverTest.java`
- Create: `app/src/main/java/com/wayne/statusbarforceclose/TaskUserIdResolver.java`
- Delete: `app/src/test/java/com/wayne/statusbarforceclose/AndroidUserIdsTest.java`
- Delete: `app/src/main/java/com/wayne/statusbarforceclose/AndroidUserIds.java`

- [ ] **Step 1: Write failing resolver tests**

Define fake task classes that cover direct and inherited integer fields with
values 0, 999, and 10. Add missing-field, wrong-type, null-task, and negative
value cases that expect `-1`.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew testDebugUnitTest --tests com.wayne.statusbarforceclose.TaskUserIdResolverTest --offline --no-daemon`

Expected: compilation failure because `TaskUserIdResolver` does not exist.

- [ ] **Step 3: Implement the minimal resolver**

Walk the runtime class hierarchy with `getDeclaredField("userId")`, accept only
non-negative integer values, and return `-1` for every unavailable or invalid
case.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same focused command and expect every resolver test to pass.

- [ ] **Step 5: Remove the obsolete UID-derived helper and test**

Delete `AndroidUserIds` only after the replacement helper is green.

### Task 3: Integrate Resolution And Diagnostics

**Files:**
- Modify: `app/src/main/java/com/wayne/statusbarforceclose/TopTaskResolver.java`
- Modify: `app/build.gradle.kts`
- Create: `.github/release-notes/0.1.1.md`

- [ ] **Step 1: Replace SystemUI UID derivation**

Resolve the user from the selected `RunningTaskInfo`. On failure, log
`reason=task-user-unavailable`, task ID, and runtime class, then return null.

- [ ] **Step 2: Improve successful target diagnostics**

Include task ID, package, resolved user ID, and sanitized label in
`target_resolved`.

- [ ] **Step 3: Advance the version**

Set `versionCode = 2` and `versionName = "0.1.1"`.

- [ ] **Step 4: Write release notes**

Document correct dual-app user targeting, policy changes, retained protections,
compatibility, and upgrade behavior without claiming unperformed device tests.

### Task 4: Review And Local Verification

**Files:**
- Review all changes since `2c002f0`

- [ ] **Step 1: Review implementation against the specification**

Check every requirement, inspect the complete diff, and verify that both root
and Binder paths still receive the resolved user ID.

- [ ] **Step 2: Run the full local gate**

Run: `./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease --offline --no-daemon`

Expected: BUILD SUCCESSFUL with all tests passing and no lint errors.

- [ ] **Step 3: Inspect artifacts**

Verify package/version/SDK, API 102 metadata, SystemUI scope, no legacy Xposed
entry, no Android components or permissions, debug diagnostics present, and
release diagnostics absent.

- [ ] **Step 4: Commit implementation**

Commit source, tests, version, and release notes after fresh verification.

### Task 5: CI And Release

**Files:**
- No additional source changes expected

- [ ] **Step 1: Push `main` and verify CI**

Push commits, watch the `Android CI` run, and require success before tagging.

- [ ] **Step 2: Create and push `2-0.1.1`**

Create an annotated tag at the verified implementation commit and confirm the
remote tag points at the same commit.

- [ ] **Step 3: Watch the signed release workflow**

Require the release workflow to test, sign, validate, upload both expected
assets, and publish a non-draft Release titled `0.1.1`.

- [ ] **Step 4: Independently verify the Release**

Download the Release APK and `SHA256SUMS` to a fresh temporary directory. Check
the checksum, certificate against `SIGNING_CERT_SHA256`, package/version/SDK,
Xposed metadata and scope, and the exact release body from
`.github/release-notes/0.1.1.md`.
