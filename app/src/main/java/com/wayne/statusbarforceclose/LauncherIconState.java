package com.wayne.statusbarforceclose;

import android.content.pm.PackageManager;

final class LauncherIconState {
    private LauncherIconState() {
    }

    static boolean isVisible(int componentState) {
        return componentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                || componentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    static int componentState(boolean visible) {
        return visible
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }
}
