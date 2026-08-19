package com.wayne.statusbarforceclose;

import com.wayne.statusbarforceclose.BridgeConfigurationParcel;

interface ISystemUiCallback {
    void onConfigurationChanged(in BridgeConfigurationParcel configuration);
}
