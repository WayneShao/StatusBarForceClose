package com.wayne.statusbarforceclose;

interface RootController {
    boolean isAlive();

    boolean forceStop(String packageName, int userId) throws Exception;

    int queryOptimizationItem(OptimizationItem item) throws Exception;

    boolean setOptimizationItem(OptimizationItem item, int value) throws Exception;
}
