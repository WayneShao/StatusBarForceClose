package com.wayne.statusbarforceclose;

interface RootOperations {
    RootConnectionState state();

    BackendResult forceStop(String packageName, int userId, boolean waitForConnection);

    void requestFreshConnection();

    void requestRecovery(RecoveryReason reason, long windowId);

    static RootOperations unavailable() {
        return new RootOperations() {
            @Override
            public RootConnectionState state() {
                return RootConnectionState.DISCONNECTED;
            }

            @Override
            public BackendResult forceStop(
                    String packageName, int userId, boolean waitForConnection) {
                return new BackendResult(BackendKind.ROOT, BackendStatus.UNSUPPORTED, 0L);
            }

            @Override
            public void requestFreshConnection() {
            }

            @Override
            public void requestRecovery(RecoveryReason reason, long windowId) {
            }
        };
    }
}
