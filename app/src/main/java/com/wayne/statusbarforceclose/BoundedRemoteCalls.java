package com.wayne.statusbarforceclose;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** A fixed number of Binder lanes. Timed-out native transactions retain their lane. */
final class BoundedRemoteCalls implements AutoCloseable {
    private final Semaphore slots;
    private final ExecutorService executor;
    private final AtomicInteger active = new AtomicInteger();

    BoundedRemoteCalls(String name, int limit) {
        slots = new Semaphore(limit);
        executor = Executors.newFixedThreadPool(limit, action -> {
            Thread thread = new Thread(action, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    <T> T call(Callable<T> operation, long timeoutMillis) throws Exception {
        if (!slots.tryAcquire()) throw new RejectedExecutionException("Remote lanes occupied");
        active.incrementAndGet();
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                T value = null;
                Throwable failure = null;
                try { value = operation.call(); } catch (Throwable error) { failure = error; }
                finally { slots.release(); active.decrementAndGet(); }
                if (failure == null) result.complete(value);
                else result.completeExceptionally(failure);
            });
        } catch (RuntimeException rejected) {
            slots.release(); active.decrementAndGet(); throw rejected;
        }
        try {
            return result.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof Exception exception) throw exception;
            throw new IllegalStateException(failed.getCause());
        }
    }

    int activeCount() { return active.get(); }
    @Override public void close() { executor.shutdownNow(); }
}
