package com.wayne.statusbarforceclose;

interface BridgeStateRepository {
    BridgeStateSnapshot load();

    boolean commit(BridgeStateSnapshot snapshot);
}
