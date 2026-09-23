package com.wayne.statusbarforceclose;

interface IRootActivityController {
    boolean forceStop(String packageName, int userId, long deadlineElapsedRealtime);
    int queryOptimizationItem(int item);
    boolean setOptimizationItem(int item, int value);
}
