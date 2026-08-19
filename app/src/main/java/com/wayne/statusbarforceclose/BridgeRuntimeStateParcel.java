package com.wayne.statusbarforceclose;

import android.os.Parcel;
import android.os.Parcelable;

public final class BridgeRuntimeStateParcel implements Parcelable {
    public static final Creator<BridgeRuntimeStateParcel> CREATOR = new Creator<>() {
        @Override
        public BridgeRuntimeStateParcel createFromParcel(Parcel source) {
            return new BridgeRuntimeStateParcel(
                    source.readInt(), source.readInt() != 0, source.readInt() != 0);
        }

        @Override
        public BridgeRuntimeStateParcel[] newArray(int size) {
            return new BridgeRuntimeStateParcel[size];
        }
    };

    public final int rootState;
    public final boolean systemUiConnected;
    public final boolean backgroundOptimizationEnabled;

    public BridgeRuntimeStateParcel(
            int rootState,
            boolean systemUiConnected,
            boolean backgroundOptimizationEnabled) {
        this.rootState = rootState;
        this.systemUiConnected = systemUiConnected;
        this.backgroundOptimizationEnabled = backgroundOptimizationEnabled;
    }

    static BridgeRuntimeStateParcel fromModel(BridgeRuntimeState model) {
        return new BridgeRuntimeStateParcel(
                model.rootState().ordinal(),
                model.systemUiConnected(),
                model.backgroundOptimizationEnabled());
    }

    BridgeRuntimeState toModel() {
        return new BridgeRuntimeState(
                RootConnectionState.values()[rootState],
                systemUiConnected,
                backgroundOptimizationEnabled);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeInt(rootState);
        destination.writeInt(systemUiConnected ? 1 : 0);
        destination.writeInt(backgroundOptimizationEnabled ? 1 : 0);
    }
}
