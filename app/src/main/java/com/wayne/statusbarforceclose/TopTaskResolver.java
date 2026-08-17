package com.wayne.statusbarforceclose;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import java.util.List;

final class TopTaskResolver {
    private TopTaskResolver() {
    }

    @SuppressWarnings("deprecation")
    static TopTask resolve(Context context) {
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return null;
        }

        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }

        ActivityManager.RunningTaskInfo task = tasks.get(0);
        ComponentName topActivity = task.topActivity;
        if (topActivity == null) {
            return null;
        }

        String packageName = topActivity.getPackageName();
        String activeInputMethod = getActiveInputMethodPackage(context);
        if (!TopTaskPolicy.canForceStop(packageName, activeInputMethod)) {
            return null;
        }

        int userId = AndroidUserIds.fromUid(Process.myUid());
        if (userId < 0) {
            return null;
        }
        return new TopTask(packageName, userId, getApplicationLabel(context, packageName));
    }

    private static String getActiveInputMethodPackage(Context context) {
        String flattened = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        ComponentName component = ComponentName.unflattenFromString(flattened);
        return component == null ? null : component.getPackageName();
    }

    private static String getApplicationLabel(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0));
            CharSequence label = packageManager.getApplicationLabel(applicationInfo);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }
}
