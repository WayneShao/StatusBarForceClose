package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class BridgeStateCodecTest {
    @Test
    public void roundTripPreservesEveryPersistedField() {
        RootAttemptJournal journal = RootAttemptJournal.initial(7L)
                .consumeSettingsOpen("settings-a").journal()
                .consumeSettingsOpen("settings-b").journal()
                .consumeSystemUiGeneration("systemui-a").journal()
                .consumeSystemUiGeneration("systemui-b").journal()
                .recordTerminal(RootConnectionState.DENIED);
        OptimizationJournal optimization = OptimizationJournal.initial()
                .startGeneration()
                .putItem(
                        OptimizationItem.DOZE_WHITELIST,
                        new OptimizationItemState(
                                0, 1, true, OptimizationResolution.APPLIED))
                .pending(OptimizationAction.RESTORE, OptimizationItem.DOZE_WHITELIST)
                .withPhase(OptimizationPhase.RESTORING);
        BridgeStateSnapshot snapshot = new BridgeStateSnapshot(
                ForceStopConfiguration.bridgeDefaults().update(ExecutionMode.SYSTEM_UI_FIRST, false),
                journal,
                optimization,
                LastExecutionRecord.successful(BackendKind.SYSTEM_UI, 37L));

        BridgeStateSnapshot decoded = BridgeStateCodec.decode(
                BridgeStateCodec.encode(snapshot), 7L);

        assertEquals(snapshot, decoded);
    }

    @Test
    public void emptyStorageCreatesBridgeDefaults() {
        BridgeStateSnapshot decoded = BridgeStateCodec.decode(Map.of(), 7L);

        assertEquals(ForceStopConfiguration.bridgeDefaults(), decoded.configuration());
        assertEquals(RootAttemptJournal.initial(7L), decoded.rootJournal());
        assertEquals(LastExecutionRecord.none(), decoded.lastExecution());
    }

    @Test
    public void schemaTwoMigratesWithoutInventingAnExecution() {
        Map<String, Object> legacy = new HashMap<>(BridgeStateCodec.encode(
                new BridgeStateSnapshot(
                        ForceStopConfiguration.bridgeDefaults(),
                        RootAttemptJournal.initial(7L))));
        legacy.put("schema_version", 2);
        legacy.keySet().removeIf(key -> key.startsWith("last_execution_"));

        BridgeStateSnapshot decoded = BridgeStateCodec.decode(legacy, 7L);

        assertTrue(decoded.configuration().isUsable());
        assertEquals(LastExecutionRecord.none(), decoded.lastExecution());
    }

    @Test
    public void unknownOrCorruptSchemaFailsClosed() {
        Map<String, Object> unknown = new HashMap<>();
        unknown.put("schema_version", 99);
        BridgeStateSnapshot unknownDecoded = BridgeStateCodec.decode(unknown, 7L);
        assertEquals(ConfigurationState.UNCONFIGURED, unknownDecoded.configuration().state());
        assertEquals(RootConnectionState.INCOMPATIBLE,
                unknownDecoded.rootJournal().terminalState());

        Map<String, Object> corrupt = new HashMap<>(
                BridgeStateCodec.encode(new BridgeStateSnapshot(
                        ForceStopConfiguration.bridgeDefaults(),
                        RootAttemptJournal.initial(7L))));
        corrupt.put("configuration_revision", "not-a-long");
        BridgeStateSnapshot corruptDecoded = BridgeStateCodec.decode(corrupt, 7L);
        assertEquals(ConfigurationState.UNCONFIGURED, corruptDecoded.configuration().state());
        assertEquals(7L, corruptDecoded.rootJournal().apkVersionCode());
        assertEquals(RootConnectionState.INCOMPATIBLE,
                corruptDecoded.rootJournal().terminalState());
    }

    @Test
    public void currentApkVersionStartsNewCompatibilityEpochOnly() {
        RootAttemptJournal oldJournal = RootAttemptJournal.initial(7L)
                .consumeSettingsOpen("settings-a").journal()
                .recordTerminal(RootConnectionState.INCOMPATIBLE);
        BridgeStateSnapshot decoded = BridgeStateCodec.decode(
                BridgeStateCodec.encode(new BridgeStateSnapshot(
                        ForceStopConfiguration.bridgeDefaults(), oldJournal)),
                8L);

        assertEquals(8L, decoded.rootJournal().apkVersionCode());
        assertNull(decoded.rootJournal().terminalState());
        assertEquals("settings-a", decoded.rootJournal().currentSettingsToken());
        assertTrue(decoded.configuration().isUsable());
        assertFalse(BridgeStateCodec.encode(decoded).keySet().stream().anyMatch(key ->
                key.contains("package") || key.contains("activity") || key.contains("task")));
    }
}
