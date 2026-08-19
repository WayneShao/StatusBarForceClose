package com.wayne.statusbarforceclose;

import java.util.List;
import java.util.Objects;

record ExecutionPlan(List<ExecutionStep> steps) {
    ExecutionPlan {
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
    }
}
