package com.wayne.statusbarforceclose;

import java.util.Objects;

record ForceStopConfiguration(
        ConfigurationState state,
        ExecutionMode executionMode,
        boolean backgroundOptimizationEnabled,
        long revision) {
    ForceStopConfiguration {
        Objects.requireNonNull(state, "state");
        if (state == ConfigurationState.UNCONFIGURED) {
            if (executionMode != null || revision != 0L) {
                throw new IllegalArgumentException("Unconfigured state must be empty");
            }
        } else {
            Objects.requireNonNull(executionMode, "executionMode");
            if (revision <= 0L) {
                throw new IllegalArgumentException("Configured revision must be positive");
            }
        }
    }

    static ForceStopConfiguration unconfigured() {
        return new ForceStopConfiguration(ConfigurationState.UNCONFIGURED, null, true, 0L);
    }

    static ForceStopConfiguration bridgeDefaults() {
        return new ForceStopConfiguration(
                ConfigurationState.CONFIGURED,
                ExecutionMode.AUTO,
                true,
                1L);
    }

    boolean isUsable() {
        return state == ConfigurationState.CONFIGURED;
    }

    ForceStopConfiguration update(
            ExecutionMode newExecutionMode,
            boolean newBackgroundOptimizationEnabled) {
        Objects.requireNonNull(newExecutionMode, "newExecutionMode");
        long nextRevision = revision == Long.MAX_VALUE ? Long.MAX_VALUE : revision + 1L;
        if (!isUsable()) {
            nextRevision = 1L;
        }
        return new ForceStopConfiguration(
                ConfigurationState.CONFIGURED,
                newExecutionMode,
                newBackgroundOptimizationEnabled,
                nextRevision);
    }

    ForceStopConfiguration acceptNewer(ForceStopConfiguration candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.isUsable()) {
            return this;
        }
        if (!isUsable() || candidate.revision > revision) {
            return candidate;
        }
        return this;
    }
}
