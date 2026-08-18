package com.wayne.statusbarforceclose;

final class RootBridgeCallerPolicy {
    private static final String SYSTEM_UI = "com.android.systemui";

    private RootBridgeCallerPolicy() {
    }

    static boolean isAllowed(String callingPackage) {
        return SYSTEM_UI.equals(callingPackage);
    }
}
