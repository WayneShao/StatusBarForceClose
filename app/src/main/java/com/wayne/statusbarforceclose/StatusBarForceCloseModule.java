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
import java.util.concurrent.atomic.AtomicLong;

import io.github.libxposed.api.XposedModule;

public final class StatusBarForceCloseModule extends XposedModule {
    private static final String TAG = "StatusBarForceClose";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final long MAX_TAP_DURATION_MS = 250L;

    private final Map<View, DoubleTapDetector> detectors =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicBoolean forceStopInFlight = new AtomicBoolean();
    private final AtomicLong requestCounter = new AtomicLong();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, TAG);
        thread.setDaemon(true);
        return thread;
    });
    private final DiagnosticLogger diagnosticLogger = this::report;
    private final ForceStopCoordinator coordinator = new ForceStopCoordinator(
            new SuForceStopMethod(diagnosticLogger),
            new BinderForceStopMethod(diagnosticLogger));

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        report(Log.INFO, "module_loaded", "process=" + param.getProcessName()
                + " api=" + getApiVersion() + " framework=" + getFrameworkName()
                + " frameworkVersion=" + getFrameworkVersion(), null);
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage() || !SYSTEM_UI.equals(param.getPackageName())) {
            return;
        }

        try {
            report(Log.INFO, "hook_install_start", "package=" + param.getPackageName(), null);
            Method touchMethod = findTouchMethod(param.getClassLoader());
            hook(touchMethod)
                    .setId("status-bar-force-close:touch-observer")
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        observeTouch(chain.getThisObject(), chain.getArg(0));
                        return result;
                    });
            report(Log.INFO, "hook_installed", "method="
                    + touchMethod.getDeclaringClass().getName() + "."
                    + touchMethod.getName(), null);
        } catch (Throwable throwable) {
            report(Log.ERROR, "hook_install_failed", "package=" + param.getPackageName(),
                    throwable);
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
            diagnosticLogger.info("double_tap_detected", "view=" + view.getClass().getName());
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
        long requestId = requestCounter.incrementAndGet();
        if (!forceStopInFlight.compareAndSet(false, true)) {
            diagnosticLogger.warn("request_ignored", "requestId=" + requestId
                    + " reason=force-stop-in-flight");
            return;
        }

        diagnosticLogger.info("request_queued", "requestId=" + requestId);
        Context applicationContext = context.getApplicationContext();
        worker.execute(() -> {
            try {
                TopTask task = TopTaskResolver.resolve(
                        applicationContext == null ? context : applicationContext,
                        diagnosticLogger);
                if (task == null) {
                    diagnosticLogger.info("request_finished", "requestId=" + requestId
                            + " result=no-eligible-target");
                    return;
                }

                diagnosticLogger.info("force_stop_start", "requestId=" + requestId
                        + " package=" + task.packageName() + " user=" + task.userId());
                ForceStopResult result = coordinator.forceStop(
                        task.packageName(), task.userId());
                diagnosticLogger.log(
                        result == ForceStopResult.FAILED ? Log.WARN : Log.INFO,
                        "force_stop_result",
                        "requestId=" + requestId + " package=" + task.packageName()
                                + " user=" + task.userId() + " result=" + result,
                        null);
                if (result != ForceStopResult.FAILED) {
                    boolean accepted = mainHandler.post(() -> showSuccessToast(
                            context, task, requestId));
                    diagnosticLogger.log(
                            accepted ? Log.INFO : Log.WARN,
                            "toast_queued",
                            "requestId=" + requestId + " package=" + task.packageName()
                                    + " accepted=" + accepted,
                            null);
                }
            } catch (Throwable throwable) {
                diagnosticLogger.error("request_exception", "requestId=" + requestId,
                        throwable);
            } finally {
                forceStopInFlight.set(false);
                diagnosticLogger.info("request_released", "requestId=" + requestId);
            }
        });
    }

    private void showSuccessToast(Context context, TopTask task, long requestId) {
        try {
            String message = ForceStopMessage.success(
                    task.applicationLabel(), task.packageName());
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            diagnosticLogger.info("toast_shown", "requestId=" + requestId + " package="
                    + task.packageName() + " message=" + message);
        } catch (Throwable throwable) {
            diagnosticLogger.error("toast_exception", "requestId=" + requestId
                    + " package=" + task.packageName(), throwable);
        }
    }

    private void report(int priority, String event, String details, Throwable throwable) {
        if (!BuildConfig.DIAGNOSTICS_ENABLED) {
            return;
        }
        String message = "event=" + event + " " + details;
        if (throwable == null) {
            log(priority, TAG, message);
            Log.println(priority, TAG, message);
        } else {
            log(priority, TAG, message, throwable);
            Log.println(priority, TAG, message + "\n" + Log.getStackTraceString(throwable));
        }
    }
}
