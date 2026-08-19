package com.wayne.statusbarforceclose;

final class RootBridgeCallerPolicy {
    private static final String SYSTEM_UI = "com.android.systemui";

    private RootBridgeCallerPolicy() {
    }

    static boolean isAllowed(String[] callingPackages) {
        return isSystemUi(callingPackages);
    }

    static boolean isSystemUi(String[] callingPackages) {
        if (callingPackages == null) {
            return false;
        }
        for (String callingPackage : callingPackages) {
            if (SYSTEM_UI.equals(callingPackage)) {
                return true;
            }
        }
        return false;
    }

    static boolean isModuleUid(int callingUid, int moduleUid) {
        return callingUid >= 0 && moduleUid >= 0 && callingUid == moduleUid;
    }
}
