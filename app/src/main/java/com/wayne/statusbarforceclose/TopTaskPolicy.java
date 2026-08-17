package com.wayne.statusbarforceclose;

import java.util.Set;

final class TopTaskPolicy {
    static final String MODULE_PACKAGE = "com.wayne.statusbarforceclose";

    private static final Set<String> PROTECTED_PACKAGES = Set.of(
        "android",
        "com.android.systemui",
        "com.miui.home",
        MODULE_PACKAGE
    );

    private TopTaskPolicy() {
    }

    static boolean canForceStop(String packageName, String activeInputMethodPackage) {
        if (packageName == null || packageName.isBlank()) {
            return false;
        }
        if (PROTECTED_PACKAGES.contains(packageName)) {
            return false;
        }
        return activeInputMethodPackage == null || !packageName.equals(activeInputMethodPackage);
    }
}

