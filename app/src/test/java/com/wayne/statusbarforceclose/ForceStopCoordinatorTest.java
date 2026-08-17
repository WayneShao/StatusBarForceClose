package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ForceStopCoordinatorTest {
    @Test
    public void rootSuccessDoesNotInvokeBinderFallback() {
        AtomicInteger binderCalls = new AtomicInteger();
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> true,
                (packageName, userId) -> {
                    binderCalls.incrementAndGet();
                    return true;
                });

        assertTrue(coordinator.forceStop("com.example.reader", 0));
        assertTrue(binderCalls.get() == 0);
    }

    @Test
    public void rootFailureInvokesBinderFallback() {
        AtomicInteger binderCalls = new AtomicInteger();
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> false,
                (packageName, userId) -> {
                    binderCalls.incrementAndGet();
                    return true;
                });

        assertTrue(coordinator.forceStop("com.example.reader", 10));
        assertTrue(binderCalls.get() == 1);
    }

    @Test
    public void reportsFailureWhenBothMethodsFail() {
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> false,
                (packageName, userId) -> false);

        assertFalse(coordinator.forceStop("com.example.reader", 0));
    }
}
