package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class BackgroundOptimizationControllerTest {
    @Test
    public void enableCapturesOriginalOnceAndAppliesEverySupportedItem() {
        Fixture fixture = fixture(Map.of(
                OptimizationItem.DOZE_WHITELIST, 0,
                OptimizationItem.RUN_IN_BACKGROUND, 1,
                OptimizationItem.RUN_ANY_IN_BACKGROUND, 2));

        assertTrue(fixture.controller.setEnabled(true));
        assertEquals(1, fixture.root.value(OptimizationItem.DOZE_WHITELIST));
        assertEquals(0, fixture.root.value(OptimizationItem.RUN_IN_BACKGROUND));
        assertEquals(0, fixture.root.value(OptimizationItem.RUN_ANY_IN_BACKGROUND));
        assertEquals(OptimizationPhase.ACTIVE, fixture.repository.journal.phase());
        assertEquals(0, fixture.repository.journal.item(
                OptimizationItem.DOZE_WHITELIST).originalValue());
        assertTrue(fixture.repository.journal.item(
                OptimizationItem.DOZE_WHITELIST).changedByModule());

        int mutations = fixture.root.mutations.get();
        assertTrue(fixture.controller.setEnabled(true));
        assertEquals(mutations, fixture.root.mutations.get());
        assertEquals(1L, fixture.repository.journal.generation());
    }

    @Test
    public void preexistingDesiredValueIsNeverClaimedOrRestored() {
        Fixture fixture = fixture(Map.of(
                OptimizationItem.DOZE_WHITELIST, 1,
                OptimizationItem.RUN_IN_BACKGROUND, 0,
                OptimizationItem.RUN_ANY_IN_BACKGROUND, 0));

        assertTrue(fixture.controller.setEnabled(true));
        assertEquals(0, fixture.root.mutations.get());
        for (OptimizationItem item : OptimizationItem.values()) {
            assertFalse(fixture.repository.journal.item(item).changedByModule());
        }
        assertTrue(fixture.controller.setEnabled(false));
        assertEquals(0, fixture.root.mutations.get());
    }

    @Test
    public void disableRestoresExactRawValuesChangedByModule() {
        Fixture fixture = fixture(Map.of(
                OptimizationItem.DOZE_WHITELIST, 0,
                OptimizationItem.RUN_IN_BACKGROUND, 1,
                OptimizationItem.RUN_ANY_IN_BACKGROUND, 3));
        assertTrue(fixture.controller.setEnabled(true));

        assertTrue(fixture.controller.setEnabled(false));

        assertEquals(0, fixture.root.value(OptimizationItem.DOZE_WHITELIST));
        assertEquals(1, fixture.root.value(OptimizationItem.RUN_IN_BACKGROUND));
        assertEquals(3, fixture.root.value(OptimizationItem.RUN_ANY_IN_BACKGROUND));
        assertEquals(OptimizationPhase.RESTORED, fixture.repository.journal.phase());
        for (OptimizationItem item : OptimizationItem.values()) {
            assertEquals(OptimizationResolution.RESTORED,
                    fixture.repository.journal.item(item).resolution());
        }
    }

    @Test
    public void ownershipDriftIsResolvedWithoutOverwritingExternalValue() {
        Fixture fixture = fixture(Map.of(
                OptimizationItem.DOZE_WHITELIST, 0,
                OptimizationItem.RUN_IN_BACKGROUND, 1,
                OptimizationItem.RUN_ANY_IN_BACKGROUND, 2));
        assertTrue(fixture.controller.setEnabled(true));
        fixture.root.values.put(OptimizationItem.RUN_ANY_IN_BACKGROUND, 4);

        assertTrue(fixture.controller.setEnabled(false));

        assertEquals(4, fixture.root.value(OptimizationItem.RUN_ANY_IN_BACKGROUND));
        assertEquals(OptimizationResolution.SKIPPED_OWNERSHIP_CONFLICT,
                fixture.repository.journal.item(
                        OptimizationItem.RUN_ANY_IN_BACKGROUND).resolution());
        assertEquals(OptimizationPhase.RESTORED, fixture.repository.journal.phase());
    }

    @Test
    public void unsupportedAndMutationFailureStayIndependentAndRetryable() {
        Fixture fixture = fixture(Map.of(
                OptimizationItem.DOZE_WHITELIST, 0,
                OptimizationItem.RUN_IN_BACKGROUND, RootSystemSettings.UNSUPPORTED,
                OptimizationItem.RUN_ANY_IN_BACKGROUND, 2));
        fixture.root.failNextMutation = OptimizationItem.DOZE_WHITELIST;

        assertFalse(fixture.controller.setEnabled(true));

        assertEquals(OptimizationResolution.FAILED_RETRYABLE,
                fixture.repository.journal.item(
                        OptimizationItem.DOZE_WHITELIST).resolution());
        assertEquals(OptimizationResolution.UNSUPPORTED,
                fixture.repository.journal.item(
                        OptimizationItem.RUN_IN_BACKGROUND).resolution());
        assertEquals(0, fixture.root.value(OptimizationItem.RUN_ANY_IN_BACKGROUND));

        assertTrue(fixture.controller.setEnabled(true));
        assertEquals(1, fixture.root.value(OptimizationItem.DOZE_WHITELIST));
        assertEquals(1L, fixture.repository.journal.generation());
    }

    @Test
    public void pendingApplyIsReconciledAfterProcessReconstruction() {
        Fixture fixture = fixture(Map.of(
                OptimizationItem.DOZE_WHITELIST, 0,
                OptimizationItem.RUN_IN_BACKGROUND, 1,
                OptimizationItem.RUN_ANY_IN_BACKGROUND, 2));
        fixture.repository.failAfterPendingItem = OptimizationItem.DOZE_WHITELIST;

        assertFalse(fixture.controller.setEnabled(true));
        assertEquals(1, fixture.root.value(OptimizationItem.DOZE_WHITELIST));
        assertEquals(OptimizationAction.APPLY,
                fixture.repository.journal.pendingAction());

        fixture.repository.failAfterPendingItem = null;
        BackgroundOptimizationController reconstructed =
                new BackgroundOptimizationController(fixture.repository, fixture.root);
        assertTrue(reconstructed.setEnabled(true));
        assertEquals(OptimizationPhase.ACTIVE, fixture.repository.journal.phase());
        assertTrue(fixture.repository.journal.item(
                OptimizationItem.DOZE_WHITELIST).changedByModule());
    }

    private static Fixture fixture(Map<OptimizationItem, Integer> initialValues) {
        FakeOptimizationRepository repository = new FakeOptimizationRepository();
        FakeRootSystemSettings root = new FakeRootSystemSettings(initialValues);
        return new Fixture(
                new BackgroundOptimizationController(repository, root), repository, root);
    }

    private record Fixture(
            BackgroundOptimizationController controller,
            FakeOptimizationRepository repository,
            FakeRootSystemSettings root) {
    }

    private static final class FakeOptimizationRepository implements OptimizationRepository {
        private OptimizationJournal journal = OptimizationJournal.initial();
        private OptimizationItem failAfterPendingItem;

        @Override
        public OptimizationJournal loadOptimizationJournal() {
            return journal;
        }

        @Override
        public boolean commitOptimizationJournal(OptimizationJournal updated) {
            if (failAfterPendingItem != null
                    && journal.pendingItem() == failAfterPendingItem
                    && journal.pendingAction() != OptimizationAction.NONE
                    && updated.pendingAction() == OptimizationAction.NONE) {
                return false;
            }
            journal = updated;
            return true;
        }
    }

    private static final class FakeRootSystemSettings implements RootSystemSettings {
        private final EnumMap<OptimizationItem, Integer> values =
                new EnumMap<>(OptimizationItem.class);
        private final AtomicInteger mutations = new AtomicInteger();
        private OptimizationItem failNextMutation;

        FakeRootSystemSettings(Map<OptimizationItem, Integer> initialValues) {
            values.putAll(initialValues);
        }

        @Override
        public int query(OptimizationItem item) {
            return values.getOrDefault(item, UNSUPPORTED);
        }

        @Override
        public boolean set(OptimizationItem item, int value) {
            mutations.incrementAndGet();
            if (item == failNextMutation) {
                failNextMutation = null;
                return false;
            }
            values.put(item, value);
            return true;
        }

        int value(OptimizationItem item) {
            return values.getOrDefault(item, UNSUPPORTED);
        }
    }
}
