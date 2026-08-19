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
    private static final String PHONE_STATUS_BAR_VIEW =
            "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String MIUI_STATUS_BAR_VIEW =
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView";
    private static final String OPLUS_STATUS_BAR_VIEW_EXTENSION =
            "com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl";
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
    private final SystemUiRuntime systemUiRuntime = new SystemUiRuntime(
            diagnosticLogger,
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
            installTouchHook(param.getClassLoader());
        } catch (Throwable throwable) {
            report(Log.ERROR, "hook_install_failed", "package=" + param.getPackageName(),
                    throwable);
        }
    }

    @SuppressLint("PrivateApi")
    private void installTouchHook(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> phoneStatusBar = findClassIfPresent(PHONE_STATUS_BAR_VIEW, classLoader);
        Class<?> miuiStatusBar = findClassIfPresent(MIUI_STATUS_BAR_VIEW, classLoader);
        Class<?> oplusExtension = findClassIfPresent(
                OPLUS_STATUS_BAR_VIEW_EXTENSION,
                classLoader);
        boolean miuiHierarchyCompatible = phoneStatusBar != null
                && miuiStatusBar != null
                && phoneStatusBar.isAssignableFrom(miuiStatusBar);
        StatusBarHookStrategySelector.Strategy strategy =
                StatusBarHookStrategySelector.select(
                        phoneStatusBar != null,
                        miuiHierarchyCompatible,
                        oplusExtension != null);
        report(Log.INFO, "hook_strategy_selected", "strategy=" + strategy
                + " phone=" + className(phoneStatusBar)
                + " miui=" + className(miuiStatusBar)
                + " oplus=" + className(oplusExtension), null);

        if (strategy == StatusBarHookStrategySelector.Strategy.MIUI_DISPATCH) {
            installRuntimeInflateHook(phoneStatusBar);
            installMiuiDispatchHook(phoneStatusBar);
            return;
        }
        if (strategy == StatusBarHookStrategySelector.Strategy.OPLUS_INFLATE_LISTENER) {
            installOplusInflateHook(phoneStatusBar);
            return;
        }
        throw new IllegalStateException("Unsupported status-bar class structure");
    }

    private void installRuntimeInflateHook(Class<?> phoneStatusBar)
            throws NoSuchMethodException {
        Method inflateMethod = phoneStatusBar.getDeclaredMethod("onFinishInflate");
        hook(inflateMethod)
                .setId("status-bar-force-close:runtime-start")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    ensureRuntimeStarted(chain.getThisObject());
                    return result;
                });
        report(Log.INFO, "hook_installed", "strategy=RUNTIME_START method="
                + inflateMethod.getDeclaringClass().getName() + "."
                + inflateMethod.getName(), null);
    }

    private void installMiuiDispatchHook(Class<?> phoneStatusBar)
            throws NoSuchMethodException {
        Method touchMethod = phoneStatusBar.getDeclaredMethod(
                "dispatchTouchEvent",
                MotionEvent.class);
        hook(touchMethod)
                .setId("status-bar-force-close:miui-touch-observer")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    observeTouch(chain.getThisObject(), chain.getArg(0));
                    return result;
                });
        report(Log.INFO, "hook_installed", "strategy=MIUI_DISPATCH method="
                + touchMethod.getDeclaringClass().getName() + "."
                + touchMethod.getName(), null);
    }

    private void installOplusInflateHook(Class<?> phoneStatusBar)
            throws NoSuchMethodException {
        Method inflateMethod = phoneStatusBar.getDeclaredMethod("onFinishInflate");
        hook(inflateMethod)
                .setId("status-bar-force-close:oplus-touch-listener")
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        ensureRuntimeStarted(chain.getThisObject());
                        attachTouchListener(chain.getThisObject());
                    } catch (Throwable throwable) {
                        diagnosticLogger.error("touch_listener_attach_failed", "view="
                                + className(chain.getThisObject()), throwable);
                    }
                    return result;
                });
        report(Log.INFO, "hook_installed", "strategy=OPLUS_INFLATE_LISTENER method="
                + inflateMethod.getDeclaringClass().getName() + "."
                + inflateMethod.getName(), null);
    }

    private void ensureRuntimeStarted(Object viewObject) {
        if (viewObject instanceof View view) {
            systemUiRuntime.ensureStarted(view.getContext());
        } else {
            diagnosticLogger.warn("systemui_runtime_start_skipped", "view="
                    + className(viewObject) + " reason=not-a-view");
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachTouchListener(Object viewObject) {
        if (!(viewObject instanceof View view)) {
            diagnosticLogger.warn("touch_listener_attach_skipped", "view="
                    + className(viewObject) + " reason=not-a-view");
            return;
        }
        view.setOnTouchListener((touchedView, event) -> {
            observeTouch(touchedView, event);
            return false;
        });
        diagnosticLogger.info("touch_listener_attached", "view="
                + view.getClass().getName());
    }

    private static Class<?> findClassIfPresent(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static String className(Object value) {
        if (value == null) {
            return "missing";
        }
        return value instanceof Class<?> type ? type.getName() : value.getClass().getName();
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
            systemUiRuntime.ensureStarted(view.getContext());
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
                ForceStopResult result = systemUiRuntime.forceStop(
                        task.packageName(), task.userId());
                diagnosticLogger.log(
                        result.isSuccess() ? Log.INFO : Log.WARN,
                        "force_stop_result",
                        "requestId=" + requestId + " package=" + task.packageName()
                                + " user=" + task.userId() + " result=" + result,
                        null);
                if (result.isSuccess()) {
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
