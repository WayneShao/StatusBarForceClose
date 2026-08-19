package com.wayne.statusbarforceclose;

import java.util.Objects;

final class ForceStopCoordinator {
    private final ForceStopMethod rootServiceMethod;
    private final ForceStopMethod binderMethod;

    ForceStopCoordinator(
            ForceStopMethod rootServiceMethod,
            ForceStopMethod binderMethod) {
        this.rootServiceMethod = rootServiceMethod;
        this.binderMethod = binderMethod;
    }

    ForceStopResult forceStop(ExecutionPlan plan, String packageName, int userId) {
        Objects.requireNonNull(plan, "plan");
        BackendResult lastResult = null;
        for (ExecutionStep step : plan.steps()) {
            ForceStopMethod method = step.backend() == BackendKind.ROOT
                    ? rootServiceMethod
                    : binderMethod;
            lastResult = method.forceStop(packageName, userId, step.waitForConnection());
            if (lastResult.backend() != step.backend()) {
                throw new IllegalStateException("Backend returned a mismatched result kind");
            }
            if (lastResult.isSuccess()) {
                return ForceStopResult.from(lastResult);
            }
        }
        return lastResult == null ? ForceStopResult.noBackend() : ForceStopResult.from(lastResult);
    }
}
