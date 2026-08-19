package com.wayne.statusbarforceclose;

import java.util.Objects;

record RootAttemptJournal(
        long apkVersionCode,
        long authorizationEpoch,
        RootConnectionState terminalState,
        long terminalEpoch,
        String currentSettingsToken,
        String previousSettingsToken,
        String currentSystemUiGeneration,
        String previousSystemUiGeneration) {
    RootAttemptJournal {
        if (apkVersionCode < 0L || authorizationEpoch < 0L || terminalEpoch < 0L) {
            throw new IllegalArgumentException("Journal counters must not be negative");
        }
        if (terminalState != null && !terminalState.isTerminal()) {
            throw new IllegalArgumentException("Only terminal root states can be persisted");
        }
        if (terminalState == null && terminalEpoch != 0L) {
            throw new IllegalArgumentException("A cleared terminal state has no terminal epoch");
        }
    }

    static RootAttemptJournal initial(long apkVersionCode) {
        return new RootAttemptJournal(
                apkVersionCode, 0L, null, 0L, null, null, null, null);
    }

    RootAttemptJournal recordTerminal(RootConnectionState state) {
        Objects.requireNonNull(state, "state");
        if (!state.isTerminal()) {
            throw new IllegalArgumentException("Only terminal root states can be persisted");
        }
        return new RootAttemptJournal(
                apkVersionCode,
                authorizationEpoch,
                state,
                authorizationEpoch,
                currentSettingsToken,
                previousSettingsToken,
                currentSystemUiGeneration,
                previousSystemUiGeneration);
    }

    TriggerResult consumeSettingsOpen(String token) {
        requireIdentifier(token, "settings token");
        if (token.equals(currentSettingsToken) || token.equals(previousSettingsToken)) {
            return new TriggerResult(this, false);
        }
        return consumeTrigger(
                token,
                currentSettingsToken,
                currentSystemUiGeneration,
                previousSystemUiGeneration,
                true);
    }

    TriggerResult consumeSystemUiGeneration(String generation) {
        requireIdentifier(generation, "SystemUI generation");
        if (generation.equals(currentSystemUiGeneration)
                || generation.equals(previousSystemUiGeneration)) {
            return new TriggerResult(this, false);
        }
        return consumeTrigger(
                generation,
                currentSystemUiGeneration,
                currentSettingsToken,
                previousSettingsToken,
                false);
    }

    RootAttemptJournal forApkVersion(long currentApkVersionCode) {
        if (currentApkVersionCode < 0L) {
            throw new IllegalArgumentException("APK version must not be negative");
        }
        if (currentApkVersionCode == apkVersionCode) {
            return this;
        }
        boolean clearIncompatible = terminalState == RootConnectionState.INCOMPATIBLE;
        return new RootAttemptJournal(
                currentApkVersionCode,
                authorizationEpoch,
                clearIncompatible ? null : terminalState,
                clearIncompatible ? 0L : terminalEpoch,
                currentSettingsToken,
                previousSettingsToken,
                currentSystemUiGeneration,
                previousSystemUiGeneration);
    }

    private TriggerResult consumeTrigger(
            String newIdentifier,
            String oldCurrentIdentifier,
            String otherCurrentIdentifier,
            String otherPreviousIdentifier,
            boolean settingsTrigger) {
        long nextEpoch = authorizationEpoch == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : authorizationEpoch + 1L;
        boolean clearDenied = terminalState == RootConnectionState.DENIED;
        RootAttemptJournal updated = new RootAttemptJournal(
                apkVersionCode,
                nextEpoch,
                clearDenied ? null : terminalState,
                clearDenied ? 0L : terminalEpoch,
                settingsTrigger ? newIdentifier : otherCurrentIdentifier,
                settingsTrigger ? oldCurrentIdentifier : otherPreviousIdentifier,
                settingsTrigger ? otherCurrentIdentifier : newIdentifier,
                settingsTrigger ? otherPreviousIdentifier : oldCurrentIdentifier);
        return new TriggerResult(
                updated,
                updated.terminalState != RootConnectionState.INCOMPATIBLE);
    }

    private static void requireIdentifier(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }

    record TriggerResult(RootAttemptJournal journal, boolean permitsAttempt) {
        TriggerResult {
            Objects.requireNonNull(journal, "journal");
        }
    }
}
