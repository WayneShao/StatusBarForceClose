package com.wayne.statusbarforceclose;

interface IRootActivityController {
    boolean forceStop(String packageName, int userId);
    int queryOptimizationItem(int item);
    boolean setOptimizationItem(int item, int value);
}
