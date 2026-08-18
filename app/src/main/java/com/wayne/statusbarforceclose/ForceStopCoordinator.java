package com.wayne.statusbarforceclose;

final class ForceStopCoordinator {
    private final ForceStopMethod rootMethod;
    private final ForceStopMethod rootBridgeMethod;
    private final ForceStopMethod binderMethod;

    ForceStopCoordinator(
            ForceStopMethod rootMethod,
            ForceStopMethod rootBridgeMethod,
            ForceStopMethod binderMethod) {
        this.rootMethod = rootMethod;
        this.rootBridgeMethod = rootBridgeMethod;
        this.binderMethod = binderMethod;
    }

    ForceStopResult forceStop(String packageName, int userId) {
        if (rootMethod.forceStop(packageName, userId)) {
            return ForceStopResult.ROOT;
        }
        if (rootBridgeMethod.forceStop(packageName, userId)) {
            return ForceStopResult.ROOT_BRIDGE;
        }
        return binderMethod.forceStop(packageName, userId)
                ? ForceStopResult.BINDER
                : ForceStopResult.FAILED;
    }
}
