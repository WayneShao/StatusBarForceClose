package com.wayne.statusbarforceclose;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class ForceStopPolicy {
    private ForceStopPolicy() {
    }

    static ExecutionPlan select(
            ExecutionMode mode,
            boolean rootConnected,
            boolean rootCanWait,
            boolean systemUiAvailable) {
        Objects.requireNonNull(mode, "mode");
        List<ExecutionStep> steps = new ArrayList<>(2);
        switch (mode) {
            case AUTO -> {
                if (rootConnected) {
                    addRoot(steps, true, false);
                    addSystemUi(steps, systemUiAvailable);
                } else if (systemUiAvailable) {
                    addSystemUi(steps, true);
                    addRoot(steps, rootCanWait, true);
                } else {
                    addRoot(steps, rootCanWait, true);
                }
            }
            case ROOT_ONLY -> addRoot(steps, rootConnected || rootCanWait, !rootConnected);
            case SYSTEM_UI_ONLY -> addSystemUi(steps, systemUiAvailable);
            case ROOT_FIRST -> {
                addRoot(steps, rootConnected || rootCanWait, !rootConnected);
                addSystemUi(steps, systemUiAvailable);
            }
            case SYSTEM_UI_FIRST -> {
                addSystemUi(steps, systemUiAvailable);
                addRoot(steps, rootConnected || rootCanWait, !rootConnected);
            }
        }
        return new ExecutionPlan(steps);
    }

    private static void addRoot(
            List<ExecutionStep> steps,
            boolean available,
            boolean waitForConnection) {
        if (available) {
            steps.add(new ExecutionStep(BackendKind.ROOT, waitForConnection));
        }
    }

    private static void addSystemUi(List<ExecutionStep> steps, boolean available) {
        if (available) {
            steps.add(new ExecutionStep(BackendKind.SYSTEM_UI, false));
        }
    }
}
