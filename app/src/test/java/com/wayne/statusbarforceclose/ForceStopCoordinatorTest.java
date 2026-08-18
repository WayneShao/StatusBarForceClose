package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ForceStopCoordinatorTest {
    @Test
    public void rootServiceSuccessDoesNotInvokeBinderFallback() {
        AtomicInteger binderCalls = new AtomicInteger();
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
                (packageName, userId) -> true,
                (packageName, userId) -> {
                    binderCalls.incrementAndGet();
                    return true;
                });

        assertEquals(
                ForceStopResult.ROOT_SERVICE,
                coordinator.forceStop("com.example.reader", 0));
        assertEquals(0, binderCalls.get());
    }

    @Test
    public void rootServiceFailureInvokesBinderFallback() {
        AtomicInteger binderCalls = new AtomicInteger();
        ForceStopCoordinator coordinator = new ForceStopCoordinator(
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
                (packageName, userId) -> false);

        assertEquals(
                ForceStopResult.FAILED,
                coordinator.forceStop("com.example.reader", 0));
    }
}
