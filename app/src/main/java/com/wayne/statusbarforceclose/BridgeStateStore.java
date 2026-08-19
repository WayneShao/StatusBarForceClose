package com.wayne.statusbarforceclose;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.Objects;

final class BridgeStateStore implements BridgeStateRepository, OptimizationRepository {
    static final String PREFERENCES_NAME = "bridge_state";

    private final SharedPreferences preferences;
    private final long currentApkVersionCode;

    BridgeStateStore(Context context, long currentApkVersionCode) {
        Objects.requireNonNull(context, "context");
        if (currentApkVersionCode < 0L) {
            throw new IllegalArgumentException("APK version must not be negative");
        }
        Context applicationContext = context.getApplicationContext();
        Context storageContext = applicationContext == null ? context : applicationContext;
        preferences = storageContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.currentApkVersionCode = currentApkVersionCode;
    }

    @Override
    public synchronized BridgeStateSnapshot load() {
        return BridgeStateCodec.decode(preferences.getAll(), currentApkVersionCode);
    }

    @Override
    public synchronized boolean commit(BridgeStateSnapshot snapshot) {
        Map<String, Object> encoded = BridgeStateCodec.encode(snapshot);
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, Object> entry : encoded.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String stringValue) {
                editor.putString(entry.getKey(), stringValue);
            } else if (value instanceof Boolean booleanValue) {
                editor.putBoolean(entry.getKey(), booleanValue);
            } else if (value instanceof Integer integerValue) {
                editor.putInt(entry.getKey(), integerValue);
            } else if (value instanceof Long longValue) {
                editor.putLong(entry.getKey(), longValue);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported preference type for " + entry.getKey());
            }
        }
        return editor.commit();
    }

    @Override
    public synchronized OptimizationJournal loadOptimizationJournal() {
        return load().optimizationJournal();
    }

    @Override
    public synchronized boolean commitOptimizationJournal(OptimizationJournal journal) {
        BridgeStateSnapshot current = load();
        return commit(new BridgeStateSnapshot(
                current.configuration(),
                current.rootJournal(),
                journal,
                current.lastExecution()));
    }
}
