package com.wayne.statusbarforceclose;

final class BridgeProtocol {
    static final int VERSION = 2;

    private BridgeProtocol() {
    }

    enum Status {
        OK,
        PERMISSION_REJECTED,
        INCOMPATIBLE,
        INVALID_ARGUMENT,
        STORAGE_ERROR,
        OPERATION_FAILED
    }
}
