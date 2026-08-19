package com.wayne.statusbarforceclose;

interface OptimizationOperations {
    boolean setEnabled(boolean enabled);

    static OptimizationOperations unavailable() {
        return ignored -> false;
    }
}
