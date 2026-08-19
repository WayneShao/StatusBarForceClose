package com.wayne.statusbarforceclose;

record SystemUiRegistration(
        BridgeProtocol.Status status,
        String sessionToken,
        String callbackId,
        String replacedCallbackId,
        boolean newGeneration,
        ForceStopConfiguration configuration) {
    static SystemUiRegistration rejected(BridgeProtocol.Status status) {
        return new SystemUiRegistration(status, null, null, null, false, null);
    }
}
