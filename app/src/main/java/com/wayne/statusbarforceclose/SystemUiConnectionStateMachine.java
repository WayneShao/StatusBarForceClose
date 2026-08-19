package com.wayne.statusbarforceclose;

import java.util.UUID;

final class SystemUiConnectionStateMachine {
    private final String generation;
    private SystemUiBridgeState state = SystemUiBridgeState.DISCONNECTED;
    private String sessionToken;

    SystemUiConnectionStateMachine() {
        this(UUID.randomUUID().toString());
    }

    SystemUiConnectionStateMachine(String generation) {
        if (generation == null || generation.isBlank()) {
            throw new IllegalArgumentException("generation must not be blank");
        }
        this.generation = generation;
    }

    synchronized String generation() {
        return generation;
    }

    synchronized boolean startBinding() {
        if (state != SystemUiBridgeState.DISCONNECTED) {
            return false;
        }
        state = SystemUiBridgeState.BINDING;
        return true;
    }

    synchronized boolean onConnected() {
        if (state != SystemUiBridgeState.BINDING) {
            return false;
        }
        state = SystemUiBridgeState.REGISTERING;
        return true;
    }

    synchronized boolean onRegistered(String registeredGeneration, String token) {
        if (state != SystemUiBridgeState.REGISTERING
                || !generation.equals(registeredGeneration)
                || token == null
                || token.isBlank()) {
            return false;
        }
        sessionToken = token;
        state = SystemUiBridgeState.READY;
        return true;
    }

    synchronized void onBindFailed() {
        disconnect();
    }

    synchronized void onDisconnected() {
        disconnect();
    }

    synchronized SystemUiConnectionSnapshot snapshot() {
        return new SystemUiConnectionSnapshot(state, sessionToken);
    }

    private void disconnect() {
        sessionToken = null;
        state = SystemUiBridgeState.DISCONNECTED;
    }
}
