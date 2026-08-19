package com.wayne.statusbarforceclose;

@FunctionalInterface
interface DeadlineScheduler {
    void schedule(long deadlineMillis, Runnable action);
}
