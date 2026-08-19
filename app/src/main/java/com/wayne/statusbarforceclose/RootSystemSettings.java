package com.wayne.statusbarforceclose;

interface RootSystemSettings {
    int UNSUPPORTED = Integer.MIN_VALUE;

    int query(OptimizationItem item);

    boolean set(OptimizationItem item, int value);
}
