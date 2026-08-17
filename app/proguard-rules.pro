# libxposed loads this class by the exact name in META-INF/xposed/java_init.list.
# Keep its ABI stable while still allowing R8 to optimize disabled diagnostics.
-keep,allowoptimization class com.wayne.statusbarforceclose.StatusBarForceCloseModule { *; }
