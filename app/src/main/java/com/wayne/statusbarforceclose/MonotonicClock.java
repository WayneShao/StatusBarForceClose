package com.wayne.statusbarforceclose;

@FunctionalInterface
interface MonotonicClock {
    long nowMillis();
}
