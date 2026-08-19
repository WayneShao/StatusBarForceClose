package com.wayne.statusbarforceclose;

import android.os.Parcel;
import android.os.Parcelable;

public final class BackendResultParcel implements Parcelable {
    public static final Creator<BackendResultParcel> CREATOR = new Creator<>() {
        @Override
        public BackendResultParcel createFromParcel(Parcel source) {
            return new BackendResultParcel(source.readInt(), source.readInt(), source.readLong());
        }

        @Override
        public BackendResultParcel[] newArray(int size) {
            return new BackendResultParcel[size];
        }
    };

    public final int backend;
    public final int status;
    public final long elapsedMillis;

    public BackendResultParcel(int backend, int status, long elapsedMillis) {
        this.backend = backend;
        this.status = status;
        this.elapsedMillis = elapsedMillis;
    }

    static BackendResultParcel fromModel(BackendResult model) {
        return new BackendResultParcel(
                model.backend().ordinal(), model.status().ordinal(), model.elapsedMillis());
    }

    BackendResult toModel() {
        return new BackendResult(
                BackendKind.values()[backend], BackendStatus.values()[status], elapsedMillis);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel destination, int flags) {
        destination.writeInt(backend);
        destination.writeInt(status);
        destination.writeLong(elapsedMillis);
    }
}
