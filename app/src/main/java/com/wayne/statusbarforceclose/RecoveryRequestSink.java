package com.wayne.statusbarforceclose;

@FunctionalInterface
interface RecoveryRequestSink {
    void request(RecoveryReason reason, long windowId);
}
