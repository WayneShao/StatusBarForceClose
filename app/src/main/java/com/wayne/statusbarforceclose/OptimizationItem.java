package com.wayne.statusbarforceclose;

enum OptimizationItem {
    DOZE_WHITELIST(1),
    RUN_IN_BACKGROUND(0),
    RUN_ANY_IN_BACKGROUND(0);

    private final int desiredValue;

    OptimizationItem(int desiredValue) {
        this.desiredValue = desiredValue;
    }

    int desiredValue() {
        return desiredValue;
    }
}
