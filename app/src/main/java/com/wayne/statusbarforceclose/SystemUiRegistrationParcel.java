package com.wayne.statusbarforceclose;

import android.os.Parcel;
import android.os.Parcelable;

public final class SystemUiRegistrationParcel implements Parcelable {
    public static final Creator<SystemUiRegistrationParcel> CREATOR = new Creator<>() {
        @Override
        public SystemUiRegistrationParcel createFromParcel(Parcel source) {
            return new SystemUiRegistrationParcel(
                    source.readInt(),
                    source.readString(),
                    source.readString(),
                    source.readInt() != 0,
                    source.readTypedObject(BridgeConfigurationParcel.CREATOR));
        }

        @Override
        public SystemUiRegistrationParcel[] newArray(int size) {
            return new SystemUiRegistrationParcel[size];
        }
    };

    public final int status;
    public final String sessionToken;
    public final String callbackId;
    public final boolean newGeneration;
    public final BridgeConfigurationParcel configuration;

    public SystemUiRegistrationParcel(
            int status,
            String sessionToken,
            String callbackId,
            boolean newGeneration,
            BridgeConfigurationParcel configuration) {
        this.status = status;
        this.sessionToken = sessionToken;
        this.callbackId = callbackId;
        this.newGeneration = newGeneration;
        this.configuration = configuration;
    }

    static SystemUiRegistrationParcel fromModel(SystemUiRegistration model) {
        return new SystemUiRegistrationParcel(
                model.status().ordinal(),
                model.sessionToken(),
                model.callbackId(),
                model.newGeneration(),
                model.configuration() == null
                        ? null
                        : BridgeConfigurationParcel.fromModel(model.configuration()));
    }

    @Override
    public int describeContents() {
        return configuration == null ? 0 : configuration.describeContents();
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeInt(status);
        destination.writeString(sessionToken);
        destination.writeString(callbackId);
        destination.writeInt(newGeneration ? 1 : 0);
        destination.writeTypedObject(configuration, flags);
    }
}
