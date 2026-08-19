package com.wayne.statusbarforceclose;

import com.wayne.statusbarforceclose.BackendResultParcel;
import com.wayne.statusbarforceclose.BridgeConfigurationParcel;
import com.wayne.statusbarforceclose.BridgeRuntimeStateParcel;
import com.wayne.statusbarforceclose.IRuntimeObserver;
import com.wayne.statusbarforceclose.ISystemUiCallback;
import com.wayne.statusbarforceclose.SystemUiRegistrationParcel;

interface IForceStopBridge {
    SystemUiRegistrationParcel registerSystemUi(
            int protocol, String generation, ISystemUiCallback callback);
    int unregisterSystemUi(int protocol, String generation, String sessionToken);
    BridgeConfigurationParcel getConfiguration(
            int protocol, String generation, String sessionToken);
    BackendResultParcel forceStopRoot(
            int protocol,
            String generation,
            String sessionToken,
            String packageName,
            int userId,
            boolean waitForConnection);
    int requestRecovery(
            int protocol,
            String generation,
            String sessionToken,
            int reason,
            long windowId);
    int requestRootForSettings(int protocol, String openToken);
    int updateConfiguration(
            int protocol, int executionMode, boolean backgroundOptimizationEnabled);
    int setBackgroundOptimization(int protocol, boolean enabled);
    int registerRuntimeObserver(int protocol, IRuntimeObserver observer);
    boolean unregisterRuntimeObserver(int protocol, IRuntimeObserver observer);
    BridgeRuntimeStateParcel getRuntimeState(int protocol);
    BridgeRuntimeStateParcel getSystemUiRuntimeState(
            int protocol, String generation, String sessionToken);
}
