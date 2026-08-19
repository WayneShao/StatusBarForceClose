package com.wayne.statusbarforceclose;

import android.os.Parcel;
import android.os.Parcelable;

public final class BridgeRuntimeStateParcel implements Parcelable {
    public static final Creator<BridgeRuntimeStateParcel> CREATOR = new Creator<>() {
        @Override
        public BridgeRuntimeStateParcel createFromParcel(Parcel source) {
            return new BridgeRuntimeStateParcel(
                    source.readInt(),
                    source.readInt() != 0,
                    source.readInt() != 0,
                    source.readInt(),
                    source.readInt(),
                    source.readLong());
        }

        @Override
        public BridgeRuntimeStateParcel[] newArray(int size) {
            return new BridgeRuntimeStateParcel[size];
        }
    };

    public final int rootState;
    public final boolean systemUiConnected;
    public final boolean backgroundOptimizationEnabled;
    public final int systemUiCapability;
    public final int lastExecutionBackend;
    public final long lastExecutionElapsedMillis;

    public BridgeRuntimeStateParcel(
            int rootState,
            boolean systemUiConnected,
            boolean backgroundOptimizationEnabled,
            int systemUiCapability,
            int lastExecutionBackend,
            long lastExecutionElapsedMillis) {
        this.rootState = rootState;
        this.systemUiConnected = systemUiConnected;
        this.backgroundOptimizationEnabled = backgroundOptimizationEnabled;
        this.systemUiCapability = systemUiCapability;
        this.lastExecutionBackend = lastExecutionBackend;
        this.lastExecutionElapsedMillis = lastExecutionElapsedMillis;
    }

    static BridgeRuntimeStateParcel fromModel(BridgeRuntimeState model) {
        return new BridgeRuntimeStateParcel(
                model.rootState().ordinal(),
                model.systemUiConnected(),
                model.backgroundOptimizationEnabled(),
                model.systemUiCapability().ordinal(),
                model.lastExecution().isPresent()
                        ? model.lastExecution().backend().ordinal()
                        : -1,
                model.lastExecution().elapsedMillis());
    }

    BridgeRuntimeState toModel() {
        RootConnectionState parsedRoot = enumValue(RootConnectionState.values(), rootState);
        SystemUiCapability parsedCapability = enumValue(
                SystemUiCapability.values(), systemUiCapability);
        BackendKind parsedBackend = lastExecutionBackend == -1
                ? null
                : enumValue(BackendKind.values(), lastExecutionBackend);
        LastExecutionRecord lastExecution = parsedBackend == null
                ? LastExecutionRecord.none()
                : LastExecutionRecord.successful(parsedBackend, lastExecutionElapsedMillis);
        return new BridgeRuntimeState(
                parsedRoot,
                systemUiConnected,
                backgroundOptimizationEnabled,
                parsedCapability,
                lastExecution);
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
        destination.writeInt(systemUiCapability);
        destination.writeInt(lastExecutionBackend);
        destination.writeLong(lastExecutionElapsedMillis);
    }

    private static <T> T enumValue(T[] values, int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid enum ordinal " + ordinal);
        }
        return values[ordinal];
    }
}
