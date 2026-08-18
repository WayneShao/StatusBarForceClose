package com.wayne.statusbarforceclose;

final class StatusBarHookStrategySelector {
    enum Strategy {
        MIUI_DISPATCH,
        OPLUS_INFLATE_LISTENER,
        UNSUPPORTED
    }

    private StatusBarHookStrategySelector() {
    }

    static Strategy select(
            boolean phoneStatusBarAvailable,
            boolean miuiHierarchyCompatible,
            boolean oplusMarkerAvailable) {
        if (!phoneStatusBarAvailable) {
            return Strategy.UNSUPPORTED;
        }
        if (miuiHierarchyCompatible) {
            return Strategy.MIUI_DISPATCH;
        }
        return oplusMarkerAvailable
                ? Strategy.OPLUS_INFLATE_LISTENER
                : Strategy.UNSUPPORTED;
    }
}
