package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public final class SettingsActivityTest {
    @Test
    public void launchAndRecreateRetainCompleteSettingsSurface() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            assertSurface(scenario);
            scenario.recreate();
            assertSurface(scenario);
        }
    }

    @Test
    public void executionModeAndSystemSettingsRowsAreInteractive() {
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            AtomicBoolean opened = new AtomicBoolean();
            long deadline = android.os.SystemClock.elapsedRealtime() + 5_000L;
            while (!opened.get() && android.os.SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity(activity -> {
                    View modeRow = activity.findViewById(R.id.execution_mode_row);
                    View settingsRow = activity.findViewById(R.id.system_settings_row);
                    assertTrue(modeRow.isClickable());
                    assertTrue(settingsRow.isClickable());
                    modeRow.performClick();
                    opened.set(activity.currentModeDialogForTest() != null);
                });
                if (!opened.get()) {
                    android.os.SystemClock.sleep(100L);
                }
            }
            assertTrue(opened.get());
            scenario.onActivity(activity -> {
                Switch optimization = activity.findViewById(
                        R.id.background_optimization_switch);
                activity.currentModeDialogForTest().dismiss();
                boolean before = optimization.isChecked();
                assertTrue(optimization.performClick());
                assertEquals(!before, optimization.isChecked());
                assertTrue(optimization.performClick());
                assertEquals(before, optimization.isChecked());
            });
        }
    }

    private static void assertSurface(ActivityScenario<SettingsActivity> scenario) {
        scenario.onActivity(activity -> {
            TextView root = activity.findViewById(R.id.root_status_value);
            TextView systemUi = activity.findViewById(R.id.systemui_status_value);
            TextView mode = activity.findViewById(R.id.execution_mode_value);
            Switch optimization = activity.findViewById(
                    R.id.background_optimization_switch);
            assertNotNull(root.getText());
            assertNotNull(systemUi.getText());
            assertNotNull(mode.getText());
            assertEquals(View.VISIBLE, optimization.getVisibility());
            assertTrue(optimization.isEnabled());
        });
    }
}
