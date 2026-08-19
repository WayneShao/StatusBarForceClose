package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RecoveryEventsInstrumentedTest {
    @Test
    public void taskStackListenerConstructsAgainstDeviceFramework() {
        RecoveryEventController controller = controller(new AtomicInteger());

        TaskStackListenerBridge listener = new TaskStackListenerBridge(
                controller, (priority, event, details, failure) -> { });

        assertNotNull(listener);
    }

    @Test
    public void receiversRegisterSeparatelyAsExportedAndIgnoreOtherActions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicInteger requests = new AtomicInteger();
        RecoveryEventController controller = controller(requests);
        RecoveryBroadcastReceiver screenReceiver = new RecoveryBroadcastReceiver(
                RecoveryActionRouter.ACTION_SCREEN_ON, controller);
        RecoveryBroadcastReceiver userReceiver = new RecoveryBroadcastReceiver(
                RecoveryActionRouter.ACTION_USER_PRESENT, controller);
        try {
            context.registerReceiver(
                    screenReceiver,
                    new IntentFilter(RecoveryActionRouter.ACTION_SCREEN_ON),
                    Context.RECEIVER_EXPORTED);
            context.registerReceiver(
                    userReceiver,
                    new IntentFilter(RecoveryActionRouter.ACTION_USER_PRESENT),
                    Context.RECEIVER_EXPORTED);

            userReceiver.onReceive(context, new Intent(RecoveryActionRouter.ACTION_SCREEN_ON));
            assertEquals(0, requests.get());
            userReceiver.onReceive(context, new Intent(RecoveryActionRouter.ACTION_USER_PRESENT));
            assertEquals(1, requests.get());
        } finally {
            context.unregisterReceiver(screenReceiver);
            context.unregisterReceiver(userReceiver);
        }
    }

    private static RecoveryEventController controller(AtomicInteger requests) {
        Handler handler = new Handler(Looper.getMainLooper());
        return new RecoveryEventController(
                SystemClock::elapsedRealtime,
                (deadline, action) -> handler.postDelayed(
                        action, Math.max(0L, deadline - SystemClock.elapsedRealtime())),
                () -> RootConnectionState.DISCONNECTED,
                (reason, windowId) -> requests.incrementAndGet(),
                new RecoveryGate(5_000L));
    }
}
