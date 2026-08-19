package com.wayne.statusbarforceclose;

import com.wayne.statusbarforceclose.BridgeRuntimeStateParcel;

interface IRuntimeObserver {
    void onRuntimeStateChanged(in BridgeRuntimeStateParcel state);
}
