package com.wayne.statusbarforceclose;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class BridgeStateCodec {
    private static final int SCHEMA_VERSION = 2;
    private static final String SCHEMA = "schema_version";
    private static final String CONFIGURATION_STATE = "configuration_state";
    private static final String EXECUTION_MODE = "execution_mode";
    private static final String BACKGROUND_OPTIMIZATION = "background_optimization_enabled";
    private static final String CONFIGURATION_REVISION = "configuration_revision";
    private static final String APK_VERSION = "root_apk_version";
    private static final String AUTHORIZATION_EPOCH = "root_authorization_epoch";
    private static final String TERMINAL_STATE = "root_terminal_state";
    private static final String TERMINAL_EPOCH = "root_terminal_epoch";
    private static final String CURRENT_SETTINGS_TOKEN = "root_current_settings_token";
    private static final String PREVIOUS_SETTINGS_TOKEN = "root_previous_settings_token";
    private static final String CURRENT_SYSTEM_UI_GENERATION =
            "root_current_systemui_generation";
    private static final String PREVIOUS_SYSTEM_UI_GENERATION =
            "root_previous_systemui_generation";
    private static final String OPTIMIZATION_GENERATION = "optimization_generation";
    private static final String OPTIMIZATION_PHASE = "optimization_phase";
    private static final String OPTIMIZATION_PENDING_ACTION = "optimization_pending_action";
    private static final String OPTIMIZATION_PENDING_ITEM = "optimization_pending_item";

    private BridgeStateCodec() {
    }

    static Map<String, Object> encode(BridgeStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<String, Object> encoded = new LinkedHashMap<>();
        encoded.put(SCHEMA, SCHEMA_VERSION);

        ForceStopConfiguration configuration = snapshot.configuration();
        encoded.put(CONFIGURATION_STATE, configuration.state().name());
        if (configuration.executionMode() != null) {
            encoded.put(EXECUTION_MODE, configuration.executionMode().name());
        }
        encoded.put(BACKGROUND_OPTIMIZATION,
                configuration.backgroundOptimizationEnabled());
        encoded.put(CONFIGURATION_REVISION, configuration.revision());

        RootAttemptJournal journal = snapshot.rootJournal();
        encoded.put(APK_VERSION, journal.apkVersionCode());
        encoded.put(AUTHORIZATION_EPOCH, journal.authorizationEpoch());
        if (journal.terminalState() != null) {
            encoded.put(TERMINAL_STATE, journal.terminalState().name());
        }
        encoded.put(TERMINAL_EPOCH, journal.terminalEpoch());
        putIfPresent(encoded, CURRENT_SETTINGS_TOKEN, journal.currentSettingsToken());
        putIfPresent(encoded, PREVIOUS_SETTINGS_TOKEN, journal.previousSettingsToken());
        putIfPresent(encoded, CURRENT_SYSTEM_UI_GENERATION,
                journal.currentSystemUiGeneration());
        putIfPresent(encoded, PREVIOUS_SYSTEM_UI_GENERATION,
                journal.previousSystemUiGeneration());
        OptimizationJournal optimization = snapshot.optimizationJournal();
        encoded.put(OPTIMIZATION_GENERATION, optimization.generation());
        encoded.put(OPTIMIZATION_PHASE, optimization.phase().name());
        encoded.put(OPTIMIZATION_PENDING_ACTION, optimization.pendingAction().name());
        if (optimization.pendingItem() != null) {
            encoded.put(OPTIMIZATION_PENDING_ITEM, optimization.pendingItem().name());
        }
        for (OptimizationItem item : OptimizationItem.values()) {
            OptimizationItemState itemState = optimization.item(item);
            if (itemState == null) {
                continue;
            }
            String prefix = optimizationPrefix(item);
            encoded.put(prefix + "original", itemState.originalValue());
            encoded.put(prefix + "applied", itemState.appliedValue());
            encoded.put(prefix + "changed", itemState.changedByModule());
            encoded.put(prefix + "resolution", itemState.resolution().name());
        }
        return Map.copyOf(encoded);
    }

    static BridgeStateSnapshot decode(Map<String, ?> encoded, long currentApkVersionCode) {
        Objects.requireNonNull(encoded, "encoded");
        if (currentApkVersionCode < 0L) {
            throw new IllegalArgumentException("APK version must not be negative");
        }
        if (encoded.isEmpty()) {
            return defaults(currentApkVersionCode);
        }
        try {
            if (requiredInt(encoded, SCHEMA) != SCHEMA_VERSION) {
                return failClosed(currentApkVersionCode);
            }
            ConfigurationState configurationState = ConfigurationState.valueOf(
                    requiredString(encoded, CONFIGURATION_STATE));
            ForceStopConfiguration configuration;
            if (configurationState == ConfigurationState.UNCONFIGURED) {
                configuration = ForceStopConfiguration.unconfigured();
            } else {
                configuration = new ForceStopConfiguration(
                        ConfigurationState.CONFIGURED,
                        ExecutionMode.valueOf(requiredString(encoded, EXECUTION_MODE)),
                        requiredBoolean(encoded, BACKGROUND_OPTIMIZATION),
                        requiredLong(encoded, CONFIGURATION_REVISION));
            }

            String terminalName = optionalString(encoded, TERMINAL_STATE);
            RootConnectionState terminalState = terminalName == null
                    ? null
                    : RootConnectionState.valueOf(terminalName);
            RootAttemptJournal journal = new RootAttemptJournal(
                    requiredLong(encoded, APK_VERSION),
                    requiredLong(encoded, AUTHORIZATION_EPOCH),
                    terminalState,
                    requiredLong(encoded, TERMINAL_EPOCH),
                    optionalString(encoded, CURRENT_SETTINGS_TOKEN),
                    optionalString(encoded, PREVIOUS_SETTINGS_TOKEN),
                    optionalString(encoded, CURRENT_SYSTEM_UI_GENERATION),
                    optionalString(encoded, PREVIOUS_SYSTEM_UI_GENERATION));
            OptimizationAction pendingAction = OptimizationAction.valueOf(
                    requiredString(encoded, OPTIMIZATION_PENDING_ACTION));
            String pendingItemName = optionalString(encoded, OPTIMIZATION_PENDING_ITEM);
            OptimizationItem pendingItem = pendingItemName == null
                    ? null
                    : OptimizationItem.valueOf(pendingItemName);
            Map<OptimizationItem, OptimizationItemState> optimizationItems =
                    new java.util.EnumMap<>(OptimizationItem.class);
            for (OptimizationItem item : OptimizationItem.values()) {
                String prefix = optimizationPrefix(item);
                if (!encoded.containsKey(prefix + "resolution")) {
                    continue;
                }
                optimizationItems.put(item, new OptimizationItemState(
                        requiredInt(encoded, prefix + "original"),
                        requiredInt(encoded, prefix + "applied"),
                        requiredBoolean(encoded, prefix + "changed"),
                        OptimizationResolution.valueOf(
                                requiredString(encoded, prefix + "resolution"))));
            }
            OptimizationJournal optimization = new OptimizationJournal(
                    requiredLong(encoded, OPTIMIZATION_GENERATION),
                    OptimizationPhase.valueOf(requiredString(encoded, OPTIMIZATION_PHASE)),
                    pendingAction,
                    pendingItem,
                    optimizationItems);
            return new BridgeStateSnapshot(
                    configuration,
                    journal.forApkVersion(currentApkVersionCode),
                    optimization);
        } catch (RuntimeException ignored) {
            return failClosed(currentApkVersionCode);
        }
    }

    private static BridgeStateSnapshot defaults(long currentApkVersionCode) {
        return new BridgeStateSnapshot(
                ForceStopConfiguration.bridgeDefaults(),
                RootAttemptJournal.initial(currentApkVersionCode),
                OptimizationJournal.initial());
    }

    private static BridgeStateSnapshot failClosed(long currentApkVersionCode) {
        return new BridgeStateSnapshot(
                ForceStopConfiguration.unconfigured(),
                RootAttemptJournal.initial(currentApkVersionCode)
                        .recordTerminal(RootConnectionState.INCOMPATIBLE),
                OptimizationJournal.initial());
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static int requiredInt(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException("Expected integer for " + key);
        }
        return integer;
    }

    private static long requiredLong(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Long longValue)) {
            throw new IllegalArgumentException("Expected long for " + key);
        }
        return longValue;
    }

    private static boolean requiredBoolean(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException("Expected boolean for " + key);
        }
        return booleanValue;
    }

    private static String requiredString(Map<String, ?> values, String key) {
        String value = optionalString(values, key);
        if (value == null) {
            throw new IllegalArgumentException("Expected string for " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("Expected nonblank string for " + key);
        }
        return stringValue;
    }

    private static String optimizationPrefix(OptimizationItem item) {
        return "optimization_" + item.name().toLowerCase(java.util.Locale.ROOT) + '_';
    }
}
