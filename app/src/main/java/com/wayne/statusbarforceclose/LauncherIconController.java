package com.wayne.statusbarforceclose;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class LauncherIconController {
    private final PackageManager packageManager;
    private final ComponentName launcherAlias;

    LauncherIconController(Context context) {
        packageManager = context.getPackageManager();
        launcherAlias = new ComponentName(
                context.getPackageName(), context.getPackageName() + ".LauncherAlias");
    }

    boolean isVisible() {
        return LauncherIconState.isVisible(
                packageManager.getComponentEnabledSetting(launcherAlias));
    }

    void setVisible(boolean visible) {
        packageManager.setComponentEnabledSetting(
                launcherAlias,
                LauncherIconState.componentState(visible),
                PackageManager.DONT_KILL_APP);
    }
}
