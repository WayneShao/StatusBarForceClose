package com.wayne.statusbarforceclose;

enum SystemUiCapability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE_UNSUPPORTED,
    FUSED_REJECTED;

    boolean isAvailable() {
        return this == AVAILABLE;
    }
}
