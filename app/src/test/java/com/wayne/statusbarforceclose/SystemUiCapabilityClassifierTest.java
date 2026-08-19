package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class SystemUiCapabilityClassifierTest {
    @Test
    public void probeRunsOnlyOncePerClassifierInstance() {
        AtomicInteger calls = new AtomicInteger();
        SystemUiCapabilityClassifier classifier = new SystemUiCapabilityClassifier(() -> {
            calls.incrementAndGet();
            return true;
        });

        assertEquals(SystemUiCapability.AVAILABLE, classifier.capability());
        assertEquals(SystemUiCapability.AVAILABLE, classifier.capability());
        assertEquals(1, calls.get());
    }

    @Test
    public void missingForceStopApiIsUnavailableForProcessLifetime() {
        SystemUiCapabilityClassifier classifier =
                new SystemUiCapabilityClassifier(() -> false);

        assertEquals(SystemUiCapability.UNAVAILABLE_UNSUPPORTED, classifier.capability());
        classifier.record(BackendStatus.SUCCESS);

        assertEquals(SystemUiCapability.UNAVAILABLE_UNSUPPORTED, classifier.capability());
    }

    @Test
    public void permissionRejectionPermanentlyFusesSystemUiBackend() {
        SystemUiCapabilityClassifier classifier =
                new SystemUiCapabilityClassifier(() -> true);

        classifier.record(BackendStatus.PERMISSION_REJECTED);
        classifier.record(BackendStatus.SUCCESS);

        assertEquals(SystemUiCapability.FUSED_REJECTED, classifier.capability());
    }

    @Test
    public void transientAndOperationFailuresDoNotFuseBackend() {
        SystemUiCapabilityClassifier classifier =
                new SystemUiCapabilityClassifier(() -> true);

        classifier.record(BackendStatus.TRANSIENT_TRANSPORT_FAILURE);
        classifier.record(BackendStatus.OPERATION_FAILED);

        assertEquals(SystemUiCapability.AVAILABLE, classifier.capability());
    }
}
