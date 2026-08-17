package com.wayne.statusbarforceclose;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

public final class StatusBarForceCloseModule extends XposedModule {
    private static final String TAG = "StatusBarForceClose";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final long MAX_TAP_DURATION_MS = 250L;

    private final Map<View, DoubleTapDetector> detectors =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicBoolean forceStopInFlight = new AtomicBoolean();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, TAG);
        thread.setDaemon(true);
        return thread;
    });
    private final ForceStopCoordinator coordinator = new ForceStopCoordinator(
            new SuForceStopMethod(),
            new BinderForceStopMethod());

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage() || !SYSTEM_UI.equals(param.getPackageName())) {
            return;
        }

        try {
            Method touchMethod = findTouchMethod(param.getClassLoader());
            hook(touchMethod)
                    .setId("status-bar-force-close:touch-observer")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        observeTouch(chain.getThisObject(), chain.getArg(0));
                        return result;
                    });
            log(Log.INFO, TAG, "Hooked " + touchMethod.getDeclaringClass().getName()
                    + "." + touchMethod.getName());
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Failed to install status-bar touch hook", throwable);
        }
    }

    @SuppressLint("PrivateApi")
    private Method findTouchMethod(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> miuiStatusBar = Class.forName(
                "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
                false,
                classLoader);
        Class<?> phoneStatusBar = Class.forName(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView",
                false,
                classLoader);
        if (!phoneStatusBar.isAssignableFrom(miuiStatusBar)) {
            throw new IllegalStateException("Unexpected status-bar class hierarchy");
        }
        return phoneStatusBar.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
    }

    private void observeTouch(Object viewObject, Object eventObject) {
        if (!(viewObject instanceof View view) || !(eventObject instanceof MotionEvent event)) {
            return;
        }

        DoubleTapDetector detector = getDetector(view);
        boolean doubleTap = detector.onEvent(
                event.getActionMasked(),
                event.getRawX(),
                event.getRawY(),
                event.getEventTime());
        if (doubleTap) {
            requestForceStop(view.getContext());
        }
    }

    private DoubleTapDetector getDetector(View view) {
        synchronized (detectors) {
            DoubleTapDetector existing = detectors.get(view);
            if (existing != null) {
                return existing;
            }

            ViewConfiguration configuration = ViewConfiguration.get(view.getContext());
            DoubleTapDetector created = new DoubleTapDetector(
                    configuration.getScaledDoubleTapSlop(),
                    MAX_TAP_DURATION_MS,
                    ViewConfiguration.getDoubleTapTimeout());
            detectors.put(view, created);
            return created;
        }
    }

    private void requestForceStop(Context context) {
        if (!forceStopInFlight.compareAndSet(false, true)) {
            return;
        }

        Context applicationContext = context.getApplicationContext();
        worker.execute(() -> {
            try {
                TopTask task = TopTaskResolver.resolve(
                        applicationContext == null ? context : applicationContext);
                if (task == null) {
                    log(Log.INFO, TAG, "No eligible foreground application");
                    return;
                }

                boolean stopped = coordinator.forceStop(task.packageName(), task.userId());
                log(stopped ? Log.INFO : Log.WARN, TAG,
                        (stopped ? "Force-stopped " : "Failed to force-stop ")
                                + task.packageName() + " for user " + task.userId());
                if (stopped) {
                    mainHandler.post(() -> Toast.makeText(
                            context,
                            ForceStopMessage.success(task.applicationLabel(), task.packageName()),
                            Toast.LENGTH_SHORT).show());
                }
            } catch (Throwable throwable) {
                log(Log.ERROR, TAG, "Foreground application lookup failed", throwable);
            } finally {
                forceStopInFlight.set(false);
            }
        });
    }
}
