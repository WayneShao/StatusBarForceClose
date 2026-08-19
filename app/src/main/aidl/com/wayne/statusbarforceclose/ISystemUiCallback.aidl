package com.wayne.statusbarforceclose;

import com.wayne.statusbarforceclose.BridgeConfigurationParcel;
import com.wayne.statusbarforceclose.BridgeRuntimeStateParcel;

interface ISystemUiCallback {
    void onConfigurationChanged(in BridgeConfigurationParcel configuration);
    void onRuntimeStateChanged(in BridgeRuntimeStateParcel state);
}
