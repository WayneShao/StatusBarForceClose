package com.wayne.statusbarforceclose;

import java.util.Objects;

final class BackgroundOptimizationController implements OptimizationOperations {
    private final OptimizationRepository repository;
    private final RootSystemSettings rootSettings;

    BackgroundOptimizationController(
            OptimizationRepository repository, RootSystemSettings rootSettings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.rootSettings = Objects.requireNonNull(rootSettings, "rootSettings");
    }

    @Override
    public synchronized boolean setEnabled(boolean enabled) {
        OptimizationJournal journal = repository.loadOptimizationJournal();
        journal = reconcilePending(journal);
        if (journal == null) {
            return false;
        }
        return enabled ? apply(journal) : restore(journal);
    }

    private boolean apply(OptimizationJournal journal) {
        if (journal.phase() == OptimizationPhase.RESTORED
                || journal.phase() == OptimizationPhase.IDLE) {
            journal = journal.startGeneration();
            if (!repository.commitOptimizationJournal(journal)) {
                return false;
            }
        }
        boolean successful = true;
        for (OptimizationItem item : OptimizationItem.values()) {
            OptimizationItemState itemState = journal.item(item);
            if (itemState == null) {
                int original = rootSettings.query(item);
                if (original == RootSystemSettings.UNSUPPORTED) {
                    itemState = new OptimizationItemState(
                            original,
                            item.desiredValue(),
                            false,
                            OptimizationResolution.UNSUPPORTED);
                    journal = journal.putItem(item, itemState);
                    if (!repository.commitOptimizationJournal(journal)) {
                        return false;
                    }
                    continue;
                }
                itemState = new OptimizationItemState(
                        original,
                        item.desiredValue(),
                        false,
                        original == item.desiredValue()
                                ? OptimizationResolution.UNCHANGED
                                : OptimizationResolution.CAPTURED);
                journal = journal.putItem(item, itemState);
                if (!repository.commitOptimizationJournal(journal)) {
                    return false;
                }
            }
            if (itemState.resolution() == OptimizationResolution.UNSUPPORTED
                    || itemState.resolution() == OptimizationResolution.UNCHANGED
                    || itemState.resolution() == OptimizationResolution.APPLIED) {
                continue;
            }

            OptimizationJournal pending = journal.pending(OptimizationAction.APPLY, item);
            if (!repository.commitOptimizationJournal(pending)) {
                return false;
            }
            boolean mutated = rootSettings.set(item, itemState.appliedValue());
            int observed = rootSettings.query(item);
            OptimizationItemState resolved = itemState.withResolution(
                    mutated && observed == itemState.appliedValue(),
                    mutated && observed == itemState.appliedValue()
                            ? OptimizationResolution.APPLIED
                            : OptimizationResolution.FAILED_RETRYABLE);
            OptimizationJournal after = pending.putItem(item, resolved).clearPending();
            if (!repository.commitOptimizationJournal(after)) {
                return false;
            }
            journal = after;
            if (resolved.resolution() == OptimizationResolution.FAILED_RETRYABLE) {
                successful = false;
            }
        }
        OptimizationJournal active = journal.withPhase(OptimizationPhase.ACTIVE);
        if (!repository.commitOptimizationJournal(active)) {
            return false;
        }
        return successful && !hasRetryableFailure(active);
    }

    private boolean restore(OptimizationJournal journal) {
        if (journal.phase() == OptimizationPhase.IDLE
                || journal.phase() == OptimizationPhase.RESTORED) {
            return true;
        }
        journal = journal.withPhase(OptimizationPhase.RESTORING);
        if (!repository.commitOptimizationJournal(journal)) {
            return false;
        }
        boolean successful = true;
        for (OptimizationItem item : OptimizationItem.values()) {
            OptimizationItemState itemState = journal.item(item);
            if (itemState == null) {
                continue;
            }
            if (!itemState.changedByModule()) {
                continue;
            }
            if (itemState.resolution() == OptimizationResolution.RESTORED
                    || itemState.resolution()
                    == OptimizationResolution.SKIPPED_OWNERSHIP_CONFLICT) {
                continue;
            }
            int observed = rootSettings.query(item);
            if (observed != itemState.appliedValue()) {
                OptimizationItemState conflict = itemState.withResolution(
                        true, OptimizationResolution.SKIPPED_OWNERSHIP_CONFLICT);
                journal = journal.putItem(item, conflict);
                if (!repository.commitOptimizationJournal(journal)) {
                    return false;
                }
                continue;
            }
            OptimizationJournal pending = journal.pending(OptimizationAction.RESTORE, item);
            if (!repository.commitOptimizationJournal(pending)) {
                return false;
            }
            boolean mutated = rootSettings.set(item, itemState.originalValue());
            int restoredValue = rootSettings.query(item);
            OptimizationItemState resolved = itemState.withResolution(
                    true,
                    mutated && restoredValue == itemState.originalValue()
                            ? OptimizationResolution.RESTORED
                            : OptimizationResolution.FAILED_RETRYABLE);
            OptimizationJournal after = pending.putItem(item, resolved).clearPending();
            if (!repository.commitOptimizationJournal(after)) {
                return false;
            }
            journal = after;
            if (resolved.resolution() == OptimizationResolution.FAILED_RETRYABLE) {
                successful = false;
            }
        }
        if (!successful || hasRetryableFailure(journal)) {
            return false;
        }
        return repository.commitOptimizationJournal(
                journal.withPhase(OptimizationPhase.RESTORED));
    }

    private OptimizationJournal reconcilePending(OptimizationJournal journal) {
        if (journal.pendingAction() == OptimizationAction.NONE) {
            return journal;
        }
        OptimizationItem item = journal.pendingItem();
        OptimizationItemState itemState = journal.item(item);
        if (itemState == null) {
            return null;
        }
        int observed = rootSettings.query(item);
        OptimizationResolution resolution;
        boolean changed = itemState.changedByModule();
        if (journal.pendingAction() == OptimizationAction.APPLY) {
            if (observed == itemState.appliedValue()) {
                resolution = OptimizationResolution.APPLIED;
                changed = true;
            } else if (observed == itemState.originalValue()) {
                resolution = OptimizationResolution.CAPTURED;
            } else {
                resolution = OptimizationResolution.FAILED_RETRYABLE;
            }
        } else if (observed == itemState.originalValue()) {
            resolution = OptimizationResolution.RESTORED;
        } else if (observed != itemState.appliedValue()) {
            resolution = OptimizationResolution.SKIPPED_OWNERSHIP_CONFLICT;
        } else {
            resolution = OptimizationResolution.FAILED_RETRYABLE;
        }
        OptimizationJournal reconciled = journal
                .putItem(item, itemState.withResolution(changed, resolution))
                .clearPending();
        return repository.commitOptimizationJournal(reconciled) ? reconciled : null;
    }

    private static boolean hasRetryableFailure(OptimizationJournal journal) {
        return journal.items().values().stream().anyMatch(item ->
                item.resolution() == OptimizationResolution.FAILED_RETRYABLE);
    }
}
