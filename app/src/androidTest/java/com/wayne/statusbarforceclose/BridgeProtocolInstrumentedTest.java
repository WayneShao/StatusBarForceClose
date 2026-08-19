package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BridgeProtocolInstrumentedTest {
    private Context context;
    private ServiceConnection connection;
    private IBinder binder;

    @Before
    public void bindBridge() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBinder> result = new AtomicReference<>();
        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                result.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };
        Intent intent = new Intent(context, ForceStopBridgeService.class);
        assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE));
        assertTrue(connected.await(5L, TimeUnit.SECONDS));
        binder = result.get();
        assertNotNull(binder);
    }

    @After
    public void unbindBridge() {
        if (connection != null) {
            context.unbindService(connection);
        }
    }

    @Test
    public void nonModuleCallerCannotIssueSettingsRootRequest() throws Exception {
        IForceStopBridge bridge = IForceStopBridge.Stub.asInterface(binder);

        int status = bridge.requestRootForSettings(BridgeProtocol.VERSION, "settings-test");

        assertEquals(BridgeProtocol.Status.PERMISSION_REJECTED.ordinal(), status);
    }

    @Test
    public void wrongProtocolFailsClosed() throws Exception {
        IForceStopBridge bridge = IForceStopBridge.Stub.asInterface(binder);

        BridgeRuntimeStateParcel state = bridge.getRuntimeState(BridgeProtocol.VERSION + 1);

        assertEquals(RootConnectionState.INCOMPATIBLE.ordinal(), state.rootState);
    }

    @Test
    public void legacyUnversionedForceStopParcelIsRejected() throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        boolean rejected = false;
        try {
            data.writeInterfaceToken(IForceStopBridge.DESCRIPTOR);
            data.writeString("com.example.reader");
            data.writeInt(0);
            binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0);
            try {
                reply.readException();
            } catch (RuntimeException expected) {
                rejected = true;
            }
            if (!rejected) {
                SystemUiRegistrationParcel parsed = reply.readTypedObject(
                        SystemUiRegistrationParcel.CREATOR);
                rejected = parsed == null
                        || parsed.status != BridgeProtocol.Status.OK.ordinal();
            }
        } finally {
            data.recycle();
            reply.recycle();
        }
        assertTrue(rejected);
    }
}
