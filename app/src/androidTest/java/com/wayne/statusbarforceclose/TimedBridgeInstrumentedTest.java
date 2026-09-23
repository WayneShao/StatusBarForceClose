package com.wayne.statusbarforceclose;

import static org.junit.Assert.*;
import android.os.RemoteException;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TimedBridgeInstrumentedTest {
    @Test(timeout = 10000)
    public void timedOutAidlCallReleasesCallerButRetainsNativeLaneUntilReturn() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (BoundedRemoteCalls calls = new BoundedRemoteCalls("test-ipc", 1)) {
            IForceStopBridge remote = (IForceStopBridge) Proxy.newProxyInstance(
                    IForceStopBridge.class.getClassLoader(), new Class<?>[] {IForceStopBridge.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getRuntimeState")) {
                            release.await();
                            return null;
                        }
                        return 0;
                    });
            IForceStopBridge bounded = TimedBridge.wrap(remote, calls);
            assertTrue(bounded.equals(bounded));
            assertFalse(bounded.equals(remote));
            long start = android.os.SystemClock.elapsedRealtime();
            assertThrows(RemoteException.class, () -> bounded.getRuntimeState(BridgeProtocol.VERSION));
            assertTrue(android.os.SystemClock.elapsedRealtime() - start < 4000L);
            assertEquals(1, calls.activeCount());
            assertThrows(RemoteException.class, () -> bounded.requestRootForSettings(5, "test"));
            release.countDown();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (calls.activeCount() != 0 && System.nanoTime() < until) Thread.yield();
            assertEquals(0, bounded.requestRootForSettings(5, "test"));
        } finally { release.countDown(); }
    }
}
