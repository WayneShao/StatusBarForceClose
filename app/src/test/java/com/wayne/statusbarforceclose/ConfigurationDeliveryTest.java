package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class ConfigurationDeliveryTest {
    @Test
    public void clientRetainsLastKnownConfigurationAfterDisconnectAndStalePush() {
        ConfigurationDelivery delivery = new ConfigurationDelivery();
        ForceStopConfiguration revisionOne = ForceStopConfiguration.bridgeDefaults();
        ForceStopConfiguration revisionTwo = revisionOne.update(ExecutionMode.ROOT_FIRST, false);

        assertEquals(revisionOne, delivery.onConfiguration(revisionOne));
        assertEquals(revisionTwo, delivery.onConfiguration(revisionTwo));
        assertEquals(revisionTwo, delivery.onConfiguration(revisionOne));
        assertEquals(revisionTwo, delivery.onDisconnected());
    }

    @Test
    public void observerRegistryReplacesAndRemovesDeadObservers() {
        BridgeObserverRegistry<String> registry = new BridgeObserverRegistry<>();
        List<String> first = new ArrayList<>();
        List<String> replacement = new ArrayList<>();
        registry.register("activity", first::add);
        registry.register("activity", replacement::add);

        registry.notifyObservers("connected");
        assertEquals(List.of(), first);
        assertEquals(List.of("connected"), replacement);
        registry.remove("activity");
        registry.notifyObservers("disconnected");
        assertEquals(List.of("connected"), replacement);
    }
}
