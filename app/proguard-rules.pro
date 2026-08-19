# libxposed loads this class by the exact name in META-INF/xposed/java_init.list.
# Keep its ABI stable while still allowing R8 to optimize disabled diagnostics.
-keep,allowoptimization class com.wayne.statusbarforceclose.StatusBarForceCloseModule { *; }

# libsu's root server creates this service from the ComponentName sent by the client process.
-keep class com.wayne.statusbarforceclose.RootActivityManagerService { *; }

# The platform invokes this callback by its hidden framework method name.
-keep class com.wayne.statusbarforceclose.TaskStackListenerBridge {
    public void onTaskMovedToFront(android.app.ActivityManager$RunningTaskInfo);
}
