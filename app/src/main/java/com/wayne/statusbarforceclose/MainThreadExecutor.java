package com.wayne.statusbarforceclose;

@FunctionalInterface
interface MainThreadExecutor {
    void execute(Runnable action);
}
