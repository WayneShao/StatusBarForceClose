package com.wayne.statusbarforceclose;

interface RootController {
    boolean isAlive();

    boolean forceStop(String packageName, int userId) throws Exception;

    default boolean forceStop(String packageName, int userId, long deadlineElapsedRealtime)
            throws Exception {
        return forceStop(packageName, userId);
    }

    int queryOptimizationItem(OptimizationItem item) throws Exception;

    boolean setOptimizationItem(OptimizationItem item, int value) throws Exception;
}
