package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RootAttemptJournalTest {
    @Test
    public void freshTriggersAdvanceEpochAndRetainDuplicateHistory() {
        RootAttemptJournal initial = RootAttemptJournal.initial(7L);

        RootAttemptJournal.TriggerResult first = initial.consumeSettingsOpen("settings-a");
        RootAttemptJournal.TriggerResult second = first.journal()
                .consumeSettingsOpen("settings-b");
        RootAttemptJournal.TriggerResult duplicateCurrent = second.journal()
                .consumeSettingsOpen("settings-b");
        RootAttemptJournal.TriggerResult duplicatePrevious = second.journal()
                .consumeSettingsOpen("settings-a");

        assertTrue(first.permitsAttempt());
        assertTrue(second.permitsAttempt());
        assertEquals(2L, second.journal().authorizationEpoch());
        assertFalse(duplicateCurrent.permitsAttempt());
        assertFalse(duplicatePrevious.permitsAttempt());
        assertSame(second.journal(), duplicateCurrent.journal());
        assertSame(second.journal(), duplicatePrevious.journal());
    }

    @Test
    public void freshSettingsOrGenerationClearsDeniedButNotIncompatible() {
        RootAttemptJournal denied = RootAttemptJournal.initial(7L)
                .recordTerminal(RootConnectionState.DENIED);
        RootAttemptJournal.TriggerResult settings = denied.consumeSettingsOpen("settings-a");
        assertTrue(settings.permitsAttempt());
        assertNull(settings.journal().terminalState());

        RootAttemptJournal deniedAgain = settings.journal()
                .recordTerminal(RootConnectionState.DENIED);
        RootAttemptJournal.TriggerResult generation = deniedAgain
                .consumeSystemUiGeneration("systemui-a");
        assertTrue(generation.permitsAttempt());
        assertNull(generation.journal().terminalState());

        RootAttemptJournal incompatible = generation.journal()
                .recordTerminal(RootConnectionState.INCOMPATIBLE);
        RootAttemptJournal.TriggerResult blocked = incompatible
                .consumeSystemUiGeneration("systemui-b");
        assertFalse(blocked.permitsAttempt());
        assertEquals(RootConnectionState.INCOMPATIBLE, blocked.journal().terminalState());
    }

    @Test
    public void apkVersionChangeClearsOnlyIncompatible() {
        RootAttemptJournal incompatible = RootAttemptJournal.initial(7L)
                .recordTerminal(RootConnectionState.INCOMPATIBLE);
        RootAttemptJournal updated = incompatible.forApkVersion(8L);
        assertEquals(8L, updated.apkVersionCode());
        assertNull(updated.terminalState());

        RootAttemptJournal denied = RootAttemptJournal.initial(7L)
                .recordTerminal(RootConnectionState.DENIED)
                .forApkVersion(8L);
        assertEquals(RootConnectionState.DENIED, denied.terminalState());
        assertSame(denied, denied.forApkVersion(8L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonTerminalStateCannotBePersistedAsTerminal() {
        RootAttemptJournal.initial(7L).recordTerminal(RootConnectionState.TRANSIENT_ERROR);
    }
}
