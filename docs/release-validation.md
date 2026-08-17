# Release validation

The signed workflow treats an APK as releasable only after all checks below pass.

## Build gates

- `testDebugUnitTest`
- `lintDebug`
- `assembleRelease` with all four signing environment variables present
- APK Signature Scheme verification through `apksigner`
- signer certificate SHA-256 matches the pinned `SIGNING_CERT_SHA256`

## Package contract

- package: `com.wayne.statusbarforceclose`
- version: Gradle `versionCode` and `versionName`
- minimum SDK: 37
- target SDK: 37
- no Activity, Service, Receiver, Provider, launcher icon, or native library

## Xposed contract

- `minApiVersion=102`
- `targetApiVersion=102`
- `staticScope=true`
- scope: only `com.android.systemui`
- entry: `com.wayne.statusbarforceclose.StatusBarForceCloseModule`
- no legacy `assets/xposed_init`

## Publication contract

Manual workflow runs upload a signed Actions artifact for testing and do not publish a Release.
Only a tag matching `versionCode-versionName` may publish a GitHub Release. The release contains
one signed APK and `SHA256SUMS`.
