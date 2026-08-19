#!/usr/bin/env bash
set -euo pipefail

apk="${1:?usage: validate-apk.sh APK diagnostics-present|diagnostics-absent}"
diagnostics="${2:?usage: validate-apk.sh APK diagnostics-present|diagnostics-absent}"
build_tools="${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}/build-tools/37.0.0"
aapt2="$build_tools/aapt2"
apkanalyzer="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/apkanalyzer"
[[ -x "$aapt2" || -f "$aapt2" ]] || aapt2="$aapt2.exe"
[[ -x "$apkanalyzer" || -f "$apkanalyzer" ]] || apkanalyzer="$apkanalyzer.bat"

[[ -f "$apk" ]] || { echo "::error::APK not found: $apk"; exit 1; }
[[ -x "$aapt2" || -f "$aapt2" ]] || {
  echo "::error::aapt2 not found: $aapt2"; exit 1;
}
[[ -x "$apkanalyzer" || -f "$apkanalyzer" ]] || {
  echo "::error::apkanalyzer not found: $apkanalyzer"; exit 1;
}
[[ "$diagnostics" == diagnostics-present || "$diagnostics" == diagnostics-absent ]] || {
  echo "::error::Unknown diagnostics expectation: $diagnostics"; exit 1;
}

badging="$($aapt2 dump badging "$apk")"
manifest="$($aapt2 dump xmltree "$apk" --file AndroidManifest.xml)"
archive_entries="$(unzip -Z1 "$apk")"
defined_symbols="$($apkanalyzer dex packages --defined-only "$apk")"

package_line="$(sed -n '1p' <<<"$badging")"
package_name="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$package_line")"
min_sdk="$(sed -n 's/^.*:minSdkVersion([^)]*)=\([0-9][0-9]*\).*$/\1/p' <<<"$manifest")"
target_sdk="$(sed -n 's/^.*:targetSdkVersion([^)]*)=\([0-9][0-9]*\).*$/\1/p' <<<"$manifest")"
application_line="$(sed -n '/^application:/{p;q;}' <<<"$badging")"

[[ "$package_name" == com.wayne.statusbarforceclose ]] || {
  echo "::error::Unexpected application package: $package_name"; exit 1;
}
[[ "$min_sdk" == 36 ]] || { echo "::error::Unexpected minSdk: $min_sdk"; exit 1; }
[[ "$target_sdk" == 37 ]] || { echo "::error::Unexpected targetSdk: $target_sdk"; exit 1; }
[[ "$application_line" == *" icon='res/"* ]] || {
  echo "::error::APK is missing its launcher icon"; exit 1;
}

activity_count="$(grep -Ec '^[[:space:]]+E: activity( |$)' <<<"$manifest" || true)"
activity_alias_count="$(grep -Ec '^[[:space:]]+E: activity-alias( |$)' <<<"$manifest" || true)"
service_count="$(grep -Ec '^[[:space:]]+E: service( |$)' <<<"$manifest" || true)"
[[ "$activity_count" == 1 ]] || {
  echo "::error::APK must declare exactly one Activity"; exit 1;
}
[[ "$activity_alias_count" == 1 ]] || {
  echo "::error::APK must declare exactly one launcher Activity alias"; exit 1;
}
[[ "$service_count" == 1 ]] || {
  echo "::error::APK must declare exactly one Service"; exit 1;
}
for component in provider receiver; do
  if grep -Eq "^[[:space:]]+E: ${component}( |$)" <<<"$manifest"; then
    echo "::error::APK unexpectedly declares an Android $component"; exit 1
  fi
done
if grep -Eq '^[[:space:]]+E: uses-permission( |$)' <<<"$manifest"; then
  echo "::error::APK unexpectedly requests an Android permission"; exit 1
fi
for required in \
  com.wayne.statusbarforceclose.SettingsActivity \
  com.wayne.statusbarforceclose.LauncherAlias \
  android.intent.action.MAIN \
  android.intent.category.LAUNCHER \
  de.robv.android.xposed.category.MODULE_SETTINGS \
  com.wayne.statusbarforceclose.ForceStopBridgeService; do
  grep -Fq "$required" <<<"$manifest" || {
    echo "::error::Manifest is missing $required"; exit 1;
  }
done

if grep -Eq '^lib/' <<<"$archive_entries"; then
  echo "::error::APK unexpectedly contains native libraries"; exit 1
fi
grep -Fxq assets/main.jar <<<"$archive_entries" || {
  echo "::error::APK is missing the libsu RootService runtime"; exit 1;
}
if grep -Fxq assets/xposed_init <<<"$archive_entries"; then
  echo "::error::APK contains the legacy Xposed entry point"; exit 1
fi

expected_module_prop=$'minApiVersion=102\ntargetApiVersion=102\nstaticScope=true'
expected_scope='com.android.systemui'
expected_entry='com.wayne.statusbarforceclose.StatusBarForceCloseModule'
actual_module_prop="$(unzip -p "$apk" META-INF/xposed/module.prop | tr -d '\r')"
actual_scope="$(unzip -p "$apk" META-INF/xposed/scope.list | tr -d '\r')"
actual_entry="$(unzip -p "$apk" META-INF/xposed/java_init.list | tr -d '\r')"
[[ "$actual_module_prop" == "$expected_module_prop" ]] || {
  echo "::error::Invalid API 102 module metadata"; exit 1;
}
[[ "$actual_scope" == "$expected_scope" ]] || {
  echo "::error::Unexpected static Xposed scope"; exit 1;
}
[[ "$actual_entry" == "$expected_entry" ]] || {
  echo "::error::Unexpected libxposed entry point"; exit 1;
}

for required_class in \
  com.wayne.statusbarforceclose.SettingsActivity \
  com.wayne.statusbarforceclose.ForceStopBridgeService \
  com.wayne.statusbarforceclose.StatusBarForceCloseModule \
  com.wayne.statusbarforceclose.TaskStackListenerBridge; do
  grep -Eq "^C [^[:space:]]+[[:space:]]+[0-9]+[[:space:]]+[0-9]+[[:space:]]+[0-9]+[[:space:]]+${required_class//./\\.}$" <<<"$defined_symbols" || {
    echo "::error::DEX is missing $required_class"; exit 1;
  }
done
if grep -Eq '^C .*[	 ]android\.app\.TaskStackListener$' <<<"$defined_symbols"; then
  echo "::error::Compile-only android.app.TaskStackListener stub leaked into the APK"; exit 1
fi
grep -Fq 'TaskStackListenerBridge void onTaskMovedToFront(' <<<"$defined_symbols" || {
  echo "::error::R8 removed the platform task callback"; exit 1;
}

dex_dir="$(mktemp -d)"
trap 'rm -rf "$dex_dir"' EXIT
unzip -qq "$apk" 'classes*.dex' -d "$dex_dir"
if [[ "$diagnostics" == diagnostics-present ]]; then
  grep -aFRq -e 'event=' -e 'double_tap_detected' "$dex_dir" || {
    echo "::error::APK is missing Debug diagnostic messages"; exit 1;
  }
elif grep -aFRq -e 'event=' -e 'double_tap_detected' "$dex_dir"; then
  echo "::error::Release APK still contains Debug diagnostic messages"; exit 1
fi

printf 'Validated %s (%s, minSdk %s, targetSdk %s)\n' \
  "$package_name" "$diagnostics" "$min_sdk" "$target_sdk"
