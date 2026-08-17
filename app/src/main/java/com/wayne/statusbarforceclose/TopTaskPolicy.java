package com.wayne.statusbarforceclose;

import java.util.Set;

final class TopTaskPolicy {
    private static final Set<String> PROTECTED_PACKAGES = Set.of(
        "android",
        "com.android.systemui",
        "com.miui.home"
    );

    private TopTaskPolicy() {
    }

    static boolean canForceStop(String packageName, String activeInputMethodPackage) {
        return rejectionReason(packageName, activeInputMethodPackage) == null;
    }

    static String rejectionReason(String packageName, String activeInputMethodPackage) {
        if (packageName == null || packageName.isBlank()) {
            return "missing-package";
        }
        if (PROTECTED_PACKAGES.contains(packageName)) {
            return "protected-package";
        }
        if (packageName.equals(activeInputMethodPackage)) {
            return "active-input-method";
        }
        return null;
    }
}
