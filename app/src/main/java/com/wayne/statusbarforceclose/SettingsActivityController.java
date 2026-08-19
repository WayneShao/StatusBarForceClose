package com.wayne.statusbarforceclose;

import java.util.Objects;
import java.util.function.Supplier;

final class SettingsActivityController {
    private final String openToken;
    private final ConfigurationDelivery configurationDelivery = new ConfigurationDelivery();
    private boolean deliveryAcknowledged;
    private boolean started;
    private boolean deliverySentThisBinding;

    SettingsActivityController(
            Supplier<String> tokenSupplier,
            String restoredToken,
            boolean restoredDeliveryAcknowledged) {
        Objects.requireNonNull(tokenSupplier, "tokenSupplier");
        openToken = isBlank(restoredToken)
                ? requireToken(tokenSupplier.get())
                : restoredToken;
        deliveryAcknowledged = restoredDeliveryAcknowledged;
    }

    String openToken() {
        return openToken;
    }

    synchronized boolean deliveryAcknowledged() {
        return deliveryAcknowledged;
    }

    synchronized boolean onStart() {
        if (started) {
            return false;
        }
        started = true;
        deliverySentThisBinding = false;
        return true;
    }

    synchronized boolean onStop() {
        if (!started) {
            return false;
        }
        started = false;
        deliverySentThisBinding = false;
        return true;
    }

    synchronized String onBridgeConnected() {
        if (!started || deliveryAcknowledged || deliverySentThisBinding) {
            return null;
        }
        deliverySentThisBinding = true;
        return openToken;
    }

    synchronized void onRootDeliveryReceipt(BridgeProtocol.Status status) {
        if (status == BridgeProtocol.Status.OK) {
            deliveryAcknowledged = true;
        }
    }

    synchronized ForceStopConfiguration onConfiguration(ForceStopConfiguration configuration) {
        return configurationDelivery.onConfiguration(configuration);
    }

    synchronized ForceStopConfiguration configuration() {
        return configurationDelivery.onDisconnected();
    }

    synchronized ConfigurationWrite selectExecutionMode(ExecutionMode mode) {
        Objects.requireNonNull(mode, "mode");
        ForceStopConfiguration current = requireConfiguration();
        return new ConfigurationWrite(mode, current.backgroundOptimizationEnabled());
    }

    synchronized ConfigurationWrite setBackgroundOptimization(boolean enabled) {
        ForceStopConfiguration current = requireConfiguration();
        return new ConfigurationWrite(current.executionMode(), enabled);
    }

    static RootUiState rootUiState(RootConnectionState state) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            case CONNECTING -> RootUiState.CONNECTING;
            case CONNECTED -> RootUiState.CONNECTED;
            default -> RootUiState.NOT_CONNECTED;
        };
    }

    private ForceStopConfiguration requireConfiguration() {
        ForceStopConfiguration current = configuration();
        if (!current.isUsable()) {
            throw new IllegalStateException("Configuration is not available");
        }
        return current;
    }

    private static String requireToken(String token) {
        if (isBlank(token)) {
            throw new IllegalArgumentException("token must not be blank");
        }
        return token;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
