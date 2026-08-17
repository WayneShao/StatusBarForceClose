package com.wayne.statusbarforceclose;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.List;

final class TopTaskResolver {
    private TopTaskResolver() {
    }

    @SuppressWarnings("deprecation")
    static TopTask resolve(Context context, DiagnosticLogger logger) {
        logger.info("target_lookup_start", "context=" + context.getClass().getName());
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        if (activityManager == null) {
            logger.warn("target_lookup_empty", "reason=activity-manager-missing");
            return null;
        }

        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (tasks == null || tasks.isEmpty()) {
            logger.warn("target_lookup_empty", "reason=no-running-task");
            return null;
        }

        ActivityManager.RunningTaskInfo task = tasks.get(0);
        ComponentName topActivity = task.topActivity;
        if (topActivity == null) {
            logger.warn("target_lookup_empty", "reason=top-activity-missing");
            return null;
        }

        String packageName = topActivity.getPackageName();
        String activeInputMethod = getActiveInputMethodPackage(context);
        String rejectionReason = TopTaskPolicy.rejectionReason(packageName, activeInputMethod);
        if (rejectionReason != null) {
            logger.info("target_rejected", "package=" + packageName + " reason="
                    + rejectionReason + " activeIme=" + activeInputMethod);
            return null;
        }

        int userId = TaskUserIdResolver.resolve(task);
        if (userId < 0) {
            logger.warn("target_lookup_empty", "reason=task-user-unavailable taskId="
                    + task.taskId + " package=" + packageName + " taskClass="
                    + task.getClass().getName());
            return null;
        }
        String applicationLabel = getApplicationLabel(context, packageName);
        logger.info("target_resolved", "taskId=" + task.taskId + " package=" + packageName
                + " user=" + userId + " label=" + sanitize(applicationLabel));
        return new TopTask(packageName, userId, applicationLabel);
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

    private static String sanitize(String value) {
        return value == null ? "null" : value.replace('\r', ' ').replace('\n', ' ');
    }
}
