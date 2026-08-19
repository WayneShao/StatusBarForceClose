package com.wayne.statusbarforceclose;

import android.os.Parcel;
import android.os.Parcelable;

public final class BridgeConfigurationParcel implements Parcelable {
    public static final Creator<BridgeConfigurationParcel> CREATOR = new Creator<>() {
        @Override
        public BridgeConfigurationParcel createFromParcel(Parcel source) {
            return new BridgeConfigurationParcel(
                    source.readInt(), source.readInt(), source.readInt() != 0, source.readLong());
        }

        @Override
        public BridgeConfigurationParcel[] newArray(int size) {
            return new BridgeConfigurationParcel[size];
        }
    };

    public final int configurationState;
    public final int executionMode;
    public final boolean backgroundOptimizationEnabled;
    public final long revision;

    public BridgeConfigurationParcel(
            int configurationState,
            int executionMode,
            boolean backgroundOptimizationEnabled,
            long revision) {
        this.configurationState = configurationState;
        this.executionMode = executionMode;
        this.backgroundOptimizationEnabled = backgroundOptimizationEnabled;
        this.revision = revision;
    }

    static BridgeConfigurationParcel fromModel(ForceStopConfiguration model) {
        return new BridgeConfigurationParcel(
                model.state().ordinal(),
                model.executionMode() == null ? -1 : model.executionMode().ordinal(),
                model.backgroundOptimizationEnabled(),
                model.revision());
    }

    ForceStopConfiguration toModel() {
        ConfigurationState state = ConfigurationState.values()[configurationState];
        if (state == ConfigurationState.UNCONFIGURED) {
            return ForceStopConfiguration.unconfigured();
        }
        return new ForceStopConfiguration(
                state,
                ExecutionMode.values()[executionMode],
                backgroundOptimizationEnabled,
                revision);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeInt(configurationState);
        destination.writeInt(executionMode);
        destination.writeInt(backgroundOptimizationEnabled ? 1 : 0);
        destination.writeLong(revision);
    }
}
