package com.wayne.statusbarforceclose;

enum RootConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    TRANSIENT_ERROR,
    DENIED,
    INCOMPATIBLE;

    boolean isTerminal() {
        return this == DENIED || this == INCOMPATIBLE;
    }

    boolean isRecoverable() {
        return this == DISCONNECTED || this == TRANSIENT_ERROR;
    }
}
