package com.wayne.statusbarforceclose;

final class ForceStopCoordinator {
    private final ForceStopMethod rootMethod;
    private final ForceStopMethod binderMethod;

    ForceStopCoordinator(ForceStopMethod rootMethod, ForceStopMethod binderMethod) {
        this.rootMethod = rootMethod;
        this.binderMethod = binderMethod;
    }

    ForceStopResult forceStop(String packageName, int userId) {
        if (rootMethod.forceStop(packageName, userId)) {
            return ForceStopResult.ROOT;
        }
        return binderMethod.forceStop(packageName, userId)
                ? ForceStopResult.BINDER
                : ForceStopResult.FAILED;
    }
}
