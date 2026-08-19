# Release validation

The signed workflow treats an APK as releasable only after all checks below pass.

## Build gates

- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- `assembleDebugAndroidTest`
- `assembleRelease` with all four signing environment variables present
- APK Signature Scheme verification through `apksigner`
- signer certificate SHA-256 matches the pinned `SIGNING_CERT_SHA256`

## Package contract

- package: `com.wayne.statusbarforceclose`
- version: Gradle `versionCode` and `versionName`
- minimum SDK: 36
- target SDK: 37
- exactly one exported launcher `SettingsActivity` with an adaptive icon
- exactly one exported `ForceStopBridgeService`
- no Provider, manifest Receiver, Android permission, or native library
- libsu `assets/main.jar` is packaged for RootService startup

## Xposed contract

- `minApiVersion=102`
- `targetApiVersion=102`
- `staticScope=true`
- scope: only `com.android.systemui`
- entry: `com.wayne.statusbarforceclose.StatusBarForceCloseModule`
- no legacy `assets/xposed_init`
- compile-only `android.app.TaskStackListener` is not defined by the APK
- `TaskStackListenerBridge.onTaskMovedToFront` survives Release shrinking

## Settings contract

- English and Simplified Chinese resources are packaged
- the Activity binds and observes the Bridge only while started
- execution mode and background protection writes cross the versioned Binder boundary
- the stored last successful result contains only backend type and elapsed time
- no foreground package, label, Activity, task ID, or user history is persisted

## Diagnostics contract

- manually dispatched signed test builds retain structured diagnostics
- tag-triggered releases compile diagnostics out and R8 removes the strings

## Publication contract

Manual workflow runs upload a signed Actions artifact and do not publish a Release. Only a tag
matching `versionCode-versionName` may publish a GitHub Release. The Release contains one signed
APK and `SHA256SUMS`; the official Xposed distribution repository mirrors the same tag, notes,
filenames, bytes, checksums, and certificate without storing source code on its default branch.
