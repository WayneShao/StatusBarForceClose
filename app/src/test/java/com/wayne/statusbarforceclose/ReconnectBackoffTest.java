package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class ReconnectBackoffTest {
    @Test
    public void retriesUseBoundedExponentialDelays() {
        ReconnectBackoff backoff = new ReconnectBackoff(1_000L, 16_000L, 5);

        assertEquals(Long.valueOf(1_000L), backoff.nextDelay());
        assertEquals(Long.valueOf(2_000L), backoff.nextDelay());
        assertEquals(Long.valueOf(4_000L), backoff.nextDelay());
        assertEquals(Long.valueOf(8_000L), backoff.nextDelay());
        assertEquals(Long.valueOf(16_000L), backoff.nextDelay());
        assertNull(backoff.nextDelay());
    }

    @Test
    public void resetStartsAVisibleTriggerAtFirstDelayAgain() {
        ReconnectBackoff backoff = new ReconnectBackoff(1_000L, 16_000L, 5);
        backoff.nextDelay();
        backoff.nextDelay();

        backoff.reset();

        assertEquals(Long.valueOf(1_000L), backoff.nextDelay());
    }
}
