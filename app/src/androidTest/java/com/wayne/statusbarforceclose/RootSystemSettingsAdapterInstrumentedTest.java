package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RootSystemSettingsAdapterInstrumentedTest {
    private Context context;
    private ServiceConnection connection;
    private IForceStopBridge bridge;

    @Before
    public void requireExplicitMutationOptIn() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("true".equals(arguments.getString("runRootSettingsMutation")));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> binder = new AtomicReference<>();
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        assertTrue(context.bindService(
                new Intent(context, ForceStopBridgeService.class),
                connection,
                Context.BIND_AUTO_CREATE));
        assertTrue(connected.await(5L, TimeUnit.SECONDS));
        bridge = IForceStopBridge.Stub.asInterface(binder.get());
        assertNotNull(bridge);
    }

    @After
    public void restoreAndUnbind() throws Exception {
        if (bridge != null) {
            bridge.setBackgroundOptimization(BridgeProtocol.VERSION, false);
        }
        if (connection != null) {
            context.unbindService(connection);
        }
    }

    @Test
    public void moduleOwnedOptimizationCanApplyAndRestore() throws Exception {
        waitForRootConnection();

        int enabled = bridge.setBackgroundOptimization(BridgeProtocol.VERSION, true);
        int restored = bridge.setBackgroundOptimization(BridgeProtocol.VERSION, false);

        assertEquals(BridgeProtocol.Status.OK.ordinal(), enabled);
        assertEquals(BridgeProtocol.Status.OK.ordinal(), restored);
    }

    private void waitForRootConnection() throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10_000L;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            BridgeRuntimeStateParcel state = bridge.getRuntimeState(BridgeProtocol.VERSION);
            if (state.rootState == RootConnectionState.CONNECTED.ordinal()) {
                return;
            }
            Thread.sleep(100L);
        }
        BridgeRuntimeStateParcel state = bridge.getRuntimeState(BridgeProtocol.VERSION);
        assertEquals(RootConnectionState.CONNECTED.ordinal(), state.rootState);
    }
}
