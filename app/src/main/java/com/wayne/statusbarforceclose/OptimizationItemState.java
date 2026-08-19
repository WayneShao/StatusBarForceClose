package com.wayne.statusbarforceclose;

import java.util.Objects;

record OptimizationItemState(
        int originalValue,
        int appliedValue,
        boolean changedByModule,
        OptimizationResolution resolution) {
    OptimizationItemState {
        Objects.requireNonNull(resolution, "resolution");
    }

    OptimizationItemState withResolution(
            boolean changed, OptimizationResolution newResolution) {
        return new OptimizationItemState(
                originalValue, appliedValue, changed, newResolution);
    }
}
