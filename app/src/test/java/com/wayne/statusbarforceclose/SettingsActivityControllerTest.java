package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class SettingsActivityControllerTest {
    @Test
    public void freshCreationGeneratesOneTokenAndBindsOncePerStart() {
        SettingsActivityController controller = controller(null, false);

        assertEquals("token-new", controller.openToken());
        assertTrue(controller.onStart());
        assertFalse(controller.onStart());
        assertTrue(controller.onStop());
        assertFalse(controller.onStop());
    }

    @Test
    public void rotationBeforeReplyRedeliversSameToken() {
        SettingsActivityController first = controller(null, false);
        first.onStart();
        assertEquals("token-new", first.onBridgeConnected());

        SettingsActivityController recreated = controller(first.openToken(), false);
        recreated.onStart();

        assertEquals("token-new", recreated.onBridgeConnected());
    }

    @Test
    public void acknowledgedDeliveryIsNeverRepeated() {
        SettingsActivityController controller = controller(null, false);
        controller.onStart();
        assertEquals("token-new", controller.onBridgeConnected());

        controller.onRootDeliveryReceipt(BridgeProtocol.Status.OK);

        assertTrue(controller.deliveryAcknowledged());
        assertNull(controller.onBridgeConnected());
        SettingsActivityController recreated = controller(
                controller.openToken(), controller.deliveryAcknowledged());
        recreated.onStart();
        assertNull(recreated.onBridgeConnected());
    }

    @Test
    public void runtimeMapsSixRootStatesToThreeUiStates() {
        assertEquals(RootUiState.CONNECTING,
                SettingsActivityController.rootUiState(RootConnectionState.CONNECTING));
        assertEquals(RootUiState.CONNECTED,
                SettingsActivityController.rootUiState(RootConnectionState.CONNECTED));
        for (RootConnectionState state : List.of(
                RootConnectionState.DISCONNECTED,
                RootConnectionState.TRANSIENT_ERROR,
                RootConnectionState.DENIED,
                RootConnectionState.INCOMPATIBLE)) {
            assertEquals(RootUiState.NOT_CONNECTED,
                    SettingsActivityController.rootUiState(state));
        }
    }

    @Test
    public void allFiveModesPreserveOptimizationAndAwaitNewerRevision() {
        SettingsActivityController controller = controller(null, false);
        ForceStopConfiguration current = ForceStopConfiguration.bridgeDefaults();
        controller.onConfiguration(current);
        List<ExecutionMode> writes = new ArrayList<>();

        for (ExecutionMode mode : ExecutionMode.values()) {
            ConfigurationWrite write = controller.selectExecutionMode(mode);
            writes.add(write.executionMode());
            assertTrue(write.backgroundOptimizationEnabled());
            assertEquals(current.revision(), controller.configuration().revision());
        }

        assertEquals(List.of(ExecutionMode.values()), writes);
        ForceStopConfiguration newer = current.update(ExecutionMode.SYSTEM_UI_FIRST, false);
        controller.onConfiguration(newer);
        controller.onConfiguration(current);
        assertEquals(newer, controller.configuration());
    }

    @Test
    public void defaultOptimizationIsEnabledAndSwitchPreservesMode() {
        SettingsActivityController controller = controller(null, false);
        controller.onConfiguration(ForceStopConfiguration.bridgeDefaults());

        assertTrue(controller.configuration().backgroundOptimizationEnabled());
        ConfigurationWrite write = controller.setBackgroundOptimization(false);

        assertEquals(ExecutionMode.AUTO, write.executionMode());
        assertFalse(write.backgroundOptimizationEnabled());
    }

    @Test
    public void settingsDestinationUsesVendorBatteryThenApplicationFallback() {
        assertEquals(SettingsDestination.VENDOR_BACKGROUND,
                SettingsIntentSelector.select(true, true));
        assertEquals(SettingsDestination.BATTERY_OPTIMIZATION,
                SettingsIntentSelector.select(false, true));
        assertEquals(SettingsDestination.APPLICATION_DETAILS,
                SettingsIntentSelector.select(false, false));
    }

    private static SettingsActivityController controller(
            String restoredToken, boolean deliveryAcknowledged) {
        return new SettingsActivityController(
                () -> "token-new", restoredToken, deliveryAcknowledged);
    }
}
