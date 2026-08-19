package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class SystemUiSessionRegistryTest {
    private static final int PROTOCOL = 3;
    private static final int SYSTEM_UI_UID = 10042;

    @Test
    public void registrationBindsOpaqueTokenToEverySessionAttribute() {
        SystemUiSessionRegistry registry = registry();
        SystemUiSessionRegistry.Registration registration = registry.register(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", "callback-a");

        assertTrue(registration.accepted());
        assertTrue(registration.newGeneration());
        assertTrue(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", registration.sessionToken()));
        assertFalse(registry.isAuthorized(
                PROTOCOL + 1, SYSTEM_UI_UID, "generation-a", registration.sessionToken()));
        assertFalse(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID + 1, "generation-a", registration.sessionToken()));
        assertFalse(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-b", registration.sessionToken()));
        assertFalse(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", "wrong-token"));
    }

    @Test
    public void sameGenerationReplacesCallbackWithoutClaimingNewGeneration() {
        SystemUiSessionRegistry registry = registry();
        SystemUiSessionRegistry.Registration first = registry.register(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", "callback-a");
        SystemUiSessionRegistry.Registration replacement = registry.register(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", "callback-b");

        assertTrue(replacement.accepted());
        assertFalse(replacement.newGeneration());
        assertNotEquals(first.sessionToken(), replacement.sessionToken());
        assertFalse(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", first.sessionToken()));
        assertTrue(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", replacement.sessionToken()));
    }

    @Test
    public void callbackDeathAndExplicitUnregisterInvalidateToken() {
        SystemUiSessionRegistry registry = registry();
        SystemUiSessionRegistry.Registration first = registry.register(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", "callback-a");
        assertFalse(registry.onCallbackDied("callback-b"));
        assertTrue(registry.onCallbackDied("callback-a"));
        assertFalse(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", first.sessionToken()));

        SystemUiSessionRegistry.Registration second = registry.register(
                PROTOCOL, SYSTEM_UI_UID, "generation-b", "callback-b");
        assertFalse(registry.unregister("wrong-token"));
        assertTrue(registry.unregister(second.sessionToken()));
        assertFalse(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-b", second.sessionToken()));
    }

    @Test
    public void protocolMismatchFailsClosedWithoutReplacingLiveSession() {
        SystemUiSessionRegistry registry = registry();
        SystemUiSessionRegistry.Registration live = registry.register(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", "callback-a");
        SystemUiSessionRegistry.Registration rejected = registry.register(
                PROTOCOL + 1, SYSTEM_UI_UID, "generation-b", "callback-b");

        assertFalse(rejected.accepted());
        assertNull(rejected.sessionToken());
        assertTrue(registry.isAuthorized(
                PROTOCOL, SYSTEM_UI_UID, "generation-a", live.sessionToken()));
    }

    private static SystemUiSessionRegistry registry() {
        AtomicInteger nextToken = new AtomicInteger();
        return new SystemUiSessionRegistry(
                PROTOCOL, () -> "opaque-" + nextToken.incrementAndGet());
    }
}
