package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ForceStopCoordinatorTest {
    @Test
    public void rootSuccessDoesNotInvokeBinderFallback() {
        AtomicInteger bridgeCalls = new AtomicInteger();
        AtomicInteger binderCalls = new AtomicInteger();
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> true,
                (packageName, userId) -> {
                    bridgeCalls.incrementAndGet();
                    return true;
                },
                (packageName, userId) -> {
                    binderCalls.incrementAndGet();
                    return true;
                });

        assertEquals(
                ForceStopResult.ROOT,
                coordinator.forceStop("com.example.reader", 0));
        assertEquals(0, bridgeCalls.get());
        assertEquals(0, binderCalls.get());
    }

    @Test
    public void rootFailureInvokesBridgeFallback() {
        AtomicInteger binderCalls = new AtomicInteger();
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> false,
                (packageName, userId) -> true,
                (packageName, userId) -> {
                    binderCalls.incrementAndGet();
                    return true;
                });

        assertEquals(
                ForceStopResult.ROOT_BRIDGE,
                coordinator.forceStop("com.example.reader", 10));
        assertEquals(0, binderCalls.get());
    }

    @Test
    public void bridgeFailureInvokesBinderFallback() {
        AtomicInteger binderCalls = new AtomicInteger();
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> false,
                (packageName, userId) -> false,
                (packageName, userId) -> {
                    binderCalls.incrementAndGet();
                    return true;
                });

        assertEquals(
                ForceStopResult.BINDER,
                coordinator.forceStop("com.example.reader", 10));
        assertEquals(1, binderCalls.get());
    }

    @Test
    public void reportsFailureWhenBothMethodsFail() {
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> false,
                (packageName, userId) -> false,
                (packageName, userId) -> false);

        assertEquals(
                ForceStopResult.FAILED,
                coordinator.forceStop("com.example.reader", 0));
    }
}
