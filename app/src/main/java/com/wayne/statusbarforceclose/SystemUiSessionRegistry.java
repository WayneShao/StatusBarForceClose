package com.wayne.statusbarforceclose;

import java.util.Objects;
import java.util.function.Supplier;

final class SystemUiSessionRegistry {
    private final int supportedProtocol;
    private final Supplier<String> tokenFactory;
    private Session activeSession;

    SystemUiSessionRegistry(int supportedProtocol, Supplier<String> tokenFactory) {
        if (supportedProtocol <= 0) {
            throw new IllegalArgumentException("supportedProtocol must be positive");
        }
        this.supportedProtocol = supportedProtocol;
        this.tokenFactory = Objects.requireNonNull(tokenFactory, "tokenFactory");
    }

    synchronized Registration register(
            int protocol,
            int callingUid,
            String generation,
            String callbackId) {
        if (protocol != supportedProtocol
                || callingUid < 0
                || isBlank(generation)
                || isBlank(callbackId)) {
            return Registration.rejected();
        }
        boolean newGeneration = activeSession == null
                || !activeSession.generation.equals(generation);
        String replacedCallbackId = activeSession == null ? null : activeSession.callbackId;
        String token = tokenFactory.get();
        if (isBlank(token)) {
            throw new IllegalStateException("Token factory returned a blank token");
        }
        activeSession = new Session(protocol, callingUid, generation, callbackId, token);
        return new Registration(true, token, newGeneration, replacedCallbackId);
    }

    synchronized boolean isAuthorized(
            int protocol,
            int callingUid,
            String generation,
            String sessionToken) {
        return activeSession != null
                && activeSession.protocol == protocol
                && activeSession.callingUid == callingUid
                && activeSession.generation.equals(generation)
                && activeSession.sessionToken.equals(sessionToken);
    }

    synchronized boolean onCallbackDied(String callbackId) {
        if (activeSession == null || !activeSession.callbackId.equals(callbackId)) {
            return false;
        }
        activeSession = null;
        return true;
    }

    synchronized boolean unregister(String sessionToken) {
        if (activeSession == null || !activeSession.sessionToken.equals(sessionToken)) {
            return false;
        }
        activeSession = null;
        return true;
    }

    synchronized boolean hasActiveSession() {
        return activeSession != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record Registration(
            boolean accepted,
            String sessionToken,
            boolean newGeneration,
            String replacedCallbackId) {
        static Registration rejected() {
            return new Registration(false, null, false, null);
        }
    }

    private record Session(
            int protocol,
            int callingUid,
            String generation,
            String callbackId,
            String sessionToken) {
    }
}
