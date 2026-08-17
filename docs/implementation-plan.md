# StatusBar Force Close Implementation Plan

**Goal:** Build a no-UI LSPosed module that force-stops the foreground app after
a valid status-bar double tap.

**Architecture:** Pure Java state/policy classes are tested independently. A
small libxposed entry point installs an observer hook in SystemUI and delegates
the privileged operation to a root-first coordinator with ActivityManager
Binder fallback.

**Tech Stack:** Android SDK 37 (minimum 37), AGP 9.2.1, Java 17,
libxposed API 102, JUnit 4.

1. Create the minimal APK and Xposed metadata.
2. Add failing tests for double-tap recognition and protected packages.
3. Implement the smallest pure Java logic that passes those tests.
4. Add the SystemUI hook and root-first foreground-task force-stop adapters.
5. Run tests, assemble the APK, and inspect manifest/resources/scope.
6. Commit the reproducible `v0.1.0` source baseline.
