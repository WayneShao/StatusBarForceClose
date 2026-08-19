package com.wayne.statusbarforceclose;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

record OptimizationJournal(
        long generation,
        OptimizationPhase phase,
        OptimizationAction pendingAction,
        OptimizationItem pendingItem,
        Map<OptimizationItem, OptimizationItemState> items) {
    OptimizationJournal {
        if (generation < 0L) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(pendingAction, "pendingAction");
        Objects.requireNonNull(items, "items");
        if ((pendingAction == OptimizationAction.NONE) != (pendingItem == null)) {
            throw new IllegalArgumentException("pending action and item must agree");
        }
        EnumMap<OptimizationItem, OptimizationItemState> copied =
                new EnumMap<>(OptimizationItem.class);
        copied.putAll(items);
        items = Collections.unmodifiableMap(copied);
    }

    static OptimizationJournal initial() {
        return new OptimizationJournal(
                0L,
                OptimizationPhase.IDLE,
                OptimizationAction.NONE,
                null,
                Map.of());
    }

    OptimizationItemState item(OptimizationItem item) {
        return items.get(item);
    }

    OptimizationJournal startGeneration() {
        long next = generation == Long.MAX_VALUE ? Long.MAX_VALUE : generation + 1L;
        return new OptimizationJournal(
                next,
                OptimizationPhase.CAPTURED,
                OptimizationAction.NONE,
                null,
                Map.of());
    }

    OptimizationJournal putItem(
            OptimizationItem item, OptimizationItemState itemState) {
        EnumMap<OptimizationItem, OptimizationItemState> updated =
                new EnumMap<>(OptimizationItem.class);
        updated.putAll(items);
        updated.put(item, itemState);
        return new OptimizationJournal(
                generation, phase, pendingAction, pendingItem, updated);
    }

    OptimizationJournal pending(OptimizationAction action, OptimizationItem item) {
        return new OptimizationJournal(generation, phase, action, item, items);
    }

    OptimizationJournal clearPending() {
        return new OptimizationJournal(
                generation, phase, OptimizationAction.NONE, null, items);
    }

    OptimizationJournal withPhase(OptimizationPhase newPhase) {
        return new OptimizationJournal(
                generation, newPhase, pendingAction, pendingItem, items);
    }
}
