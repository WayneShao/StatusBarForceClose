package com.wayne.statusbarforceclose;

final class ForceStopCoordinator {
    private final ForceStopMethod rootServiceMethod;
    private final ForceStopMethod binderMethod;

    ForceStopCoordinator(
            ForceStopMethod rootServiceMethod,
            ForceStopMethod binderMethod) {
        this.rootServiceMethod = rootServiceMethod;
        this.binderMethod = binderMethod;
    }

    ForceStopResult forceStop(String packageName, int userId) {
        if (rootServiceMethod.forceStop(packageName, userId)) {
            return ForceStopResult.ROOT_SERVICE;
        }
        return binderMethod.forceStop(packageName, userId)
                ? ForceStopResult.BINDER
                : ForceStopResult.FAILED;
    }
}
