package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BridgeStateStoreInstrumentedTest {
    private Context context;
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = context.getSharedPreferences(
                BridgeStateStore.PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertTrue(preferences.edit().clear().commit());
    }

    @After
    public void tearDown() {
        assertTrue(preferences.edit().clear().commit());
    }

    @Test
    public void synchronousCommitSurvivesAdapterRecreation() {
        BridgeStateSnapshot expected = new BridgeStateSnapshot(
                ForceStopConfiguration.bridgeDefaults().update(ExecutionMode.ROOT_FIRST, false),
                RootAttemptJournal.initial(7L)
                        .consumeSettingsOpen("settings-a").journal()
                        .recordTerminal(RootConnectionState.DENIED));

        assertTrue(new BridgeStateStore(context, 7L).commit(expected));
        BridgeStateSnapshot actual = new BridgeStateStore(context, 7L).load();

        assertEquals(expected, actual);
        assertFalse(preferences.getAll().keySet().stream().anyMatch(key ->
                key.contains("package") || key.contains("activity") || key.contains("task")));
    }

    @Test
    public void corruptRealPreferencesFailClosed() {
        assertTrue(preferences.edit().putInt("schema_version", 99).commit());

        BridgeStateSnapshot loaded = new BridgeStateStore(context, 7L).load();

        assertEquals(ConfigurationState.UNCONFIGURED, loaded.configuration().state());
        assertEquals(7L, loaded.rootJournal().apkVersionCode());
        assertEquals(RootConnectionState.INCOMPATIBLE,
                loaded.rootJournal().terminalState());
    }
}
