package com.wayne.statusbarforceclose;

import static org.junit.Assert.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class BoundedRemoteCallsTest {
    @Test(timeout = 5000)
    public void frozenTransactionsTimeOutWithoutUnboundedThreadsOrQueuedWork() throws Exception {
        BoundedRemoteCalls calls = new BoundedRemoteCalls("test", 2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger entered = new AtomicInteger();
        Callable<Integer> frozen = () -> {
            entered.incrementAndGet();
            // Binder does not stop transacting when the caller's Future is cancelled.
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
            return 9;
        };
        try {
            assertThrows(TimeoutException.class, () -> calls.call(frozen, 100));
            assertThrows(TimeoutException.class, () -> calls.call(frozen, 100));
            for (int i = 0; i < 20; i++) {
                assertThrows(RejectedExecutionException.class,
                        () -> calls.call(() -> { entered.incrementAndGet(); return 1; }, 100));
            }
            assertEquals(2, entered.get());
            release.countDown();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (calls.activeCount() != 0 && System.nanoTime() < until) Thread.yield();
            assertEquals(Integer.valueOf(42), calls.call(() -> 42, 1000));
            assertEquals(2, entered.get());
        } finally { release.countDown(); calls.close(); }
    }

    @Test
    public void propagatesFailureAndReleasesSlot() throws Exception {
        try (BoundedRemoteCalls calls = new BoundedRemoteCalls("test", 1)) {
            assertThrows(IllegalStateException.class,
                    () -> calls.call(() -> { throw new IllegalStateException("remote"); }, 1000));
            assertEquals(Integer.valueOf(1), calls.call(() -> 1, 1000));
        }
    }
}
