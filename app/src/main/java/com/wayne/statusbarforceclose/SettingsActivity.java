package com.wayne.statusbarforceclose;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends Activity {
    private static final String STATE_OPEN_TOKEN = "open_token";
    private static final String STATE_DELIVERY_ACKNOWLEDGED = "delivery_acknowledged";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bridgeWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "StatusBarForceClose-Settings");
        thread.setDaemon(true);
        return thread;
    });
    private final IRuntimeObserver runtimeObserver = new IRuntimeObserver.Stub() {
        @Override
        public void onRuntimeStateChanged(BridgeRuntimeStateParcel parcel) {
            if (parcel == null) {
                return;
            }
            try {
                BridgeRuntimeState state = parcel.toModel();
                mainHandler.post(() -> renderRuntime(state));
            } catch (RuntimeException ignored) {
                // A malformed cross-process state is ignored instead of crashing settings.
            }
        }
    };
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            IForceStopBridge connectedBridge = IForceStopBridge.Stub.asInterface(service);
            if (connectedBridge == null || !started) {
                return;
            }
            bridge = connectedBridge;
            int generation = lifecycleGeneration;
            String rootToken = controller.onBridgeConnected();
            bridgeWorker.execute(() -> initializeBridge(connectedBridge, generation, rootToken));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bridge = null;
            renderDisconnected();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            bridge = null;
            renderDisconnected();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            bridge = null;
            renderDisconnected();
        }
    };

    private SettingsActivityController controller;
    private LauncherIconController launcherIconController;
    private volatile IForceStopBridge bridge;
    private boolean started;
    private boolean bound;
    private int lifecycleGeneration;
    private boolean renderingSwitch;
    private boolean renderingLauncherIconSwitch;
    private AlertDialog currentModeDialog;
    private TextView rootStatus;
    private TextView systemUiStatus;
    private TextView systemUiBackendStatus;
    private TextView lastExecutionStatus;
    private TextView executionModeValue;
    private Switch backgroundOptimizationSwitch;
    private Switch hideLauncherIconSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String restoredToken = savedInstanceState == null
                ? null
                : savedInstanceState.getString(STATE_OPEN_TOKEN);
        boolean restoredAcknowledged = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_DELIVERY_ACKNOWLEDGED, false);
        controller = new SettingsActivityController(
                SettingsActivity::newOpenToken,
                restoredToken,
                restoredAcknowledged);
        launcherIconController = new LauncherIconController(this);
        setContentView(R.layout.activity_settings);
        bindViews();
        applySystemBarInsets();
        installListeners();
        renderInitialState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderLauncherIconState();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!controller.onStart()) {
            return;
        }
        started = true;
        lifecycleGeneration++;
        Intent intent = new Intent(this, ForceStopBridgeService.class)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException ignored) {
            bound = false;
        }
        if (!bound) {
            renderDisconnected();
        }
    }

    @Override
    protected void onStop() {
        IForceStopBridge disconnectedBridge = bridge;
        bridge = null;
        started = false;
        lifecycleGeneration++;
        controller.onStop();
        if (disconnectedBridge != null) {
            bridgeWorker.execute(() -> unregisterObserver(disconnectedBridge));
        }
        if (bound) {
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {
                // The framework already dropped the binding.
            }
            bound = false;
        }
        if (currentModeDialog != null) {
            currentModeDialog.dismiss();
            currentModeDialog = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        bridgeWorker.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_OPEN_TOKEN, controller.openToken());
        outState.putBoolean(
                STATE_DELIVERY_ACKNOWLEDGED, controller.deliveryAcknowledged());
        super.onSaveInstanceState(outState);
    }

    AlertDialog currentModeDialogForTest() {
        return currentModeDialog;
    }

    private void bindViews() {
        rootStatus = findViewById(R.id.root_status_value);
        systemUiStatus = findViewById(R.id.systemui_status_value);
        systemUiBackendStatus = findViewById(R.id.systemui_backend_status_value);
        lastExecutionStatus = findViewById(R.id.last_execution_value);
        executionModeValue = findViewById(R.id.execution_mode_value);
        backgroundOptimizationSwitch = findViewById(
                R.id.background_optimization_switch);
        hideLauncherIconSwitch = findViewById(R.id.hide_launcher_icon_switch);
    }

    private void applySystemBarInsets() {
        View root = findViewById(R.id.settings_root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void installListeners() {
        findViewById(R.id.execution_mode_row).setOnClickListener(
                ignored -> showExecutionModeDialog());
        backgroundOptimizationSwitch.setOnCheckedChangeListener(
                this::onBackgroundOptimizationChanged);
        hideLauncherIconSwitch.setOnCheckedChangeListener(
                (button, hidden) -> onLauncherIconVisibilityChanged(hidden));
        findViewById(R.id.system_settings_row).setOnClickListener(
                ignored -> openBestAvailableSettings());
        findViewById(R.id.application_settings_row).setOnClickListener(
                ignored -> startActivity(applicationDetailsIntent()));
    }

    private void renderInitialState() {
        rootStatus.setText(R.string.status_connecting);
        systemUiStatus.setText(R.string.status_not_connected);
        systemUiBackendStatus.setText(R.string.status_unknown);
        lastExecutionStatus.setText(R.string.last_execution_none);
        executionModeValue.setText(R.string.mode_auto);
        renderingSwitch = true;
        backgroundOptimizationSwitch.setChecked(true);
        renderingSwitch = false;
        renderLauncherIconState();
    }

    private void initializeBridge(
            IForceStopBridge connectedBridge, int generation, String rootToken) {
        try {
            if (rootToken != null) {
                BridgeProtocol.Status receipt = protocolStatus(
                        connectedBridge.requestRootForSettings(
                                BridgeProtocol.VERSION, rootToken));
                mainHandler.post(() -> controller.onRootDeliveryReceipt(receipt));
            }
            connectedBridge.registerRuntimeObserver(
                    BridgeProtocol.VERSION, runtimeObserver);
            BridgeConfigurationParcel configuration = connectedBridge.getModuleConfiguration(
                    BridgeProtocol.VERSION);
            BridgeRuntimeStateParcel runtime = connectedBridge.getRuntimeState(
                    BridgeProtocol.VERSION);
            if (!isCurrentConnection(connectedBridge, generation)) {
                connectedBridge.unregisterRuntimeObserver(
                        BridgeProtocol.VERSION, runtimeObserver);
                return;
            }
            if (configuration != null) {
                ForceStopConfiguration model = configuration.toModel();
                mainHandler.post(() -> renderConfiguration(model));
            }
            if (runtime != null) {
                BridgeRuntimeState model = runtime.toModel();
                mainHandler.post(() -> renderRuntime(model));
            }
        } catch (Throwable ignored) {
            mainHandler.post(this::renderDisconnected);
        }
    }

    private void unregisterObserver(IForceStopBridge disconnectedBridge) {
        try {
            disconnectedBridge.unregisterRuntimeObserver(
                    BridgeProtocol.VERSION, runtimeObserver);
        } catch (Throwable ignored) {
            // The observer disappears with the Binder if unregistration races service death.
        }
    }

    private void renderConfiguration(ForceStopConfiguration configuration) {
        if (!started || !configuration.isUsable()) {
            return;
        }
        ForceStopConfiguration accepted = controller.onConfiguration(configuration);
        executionModeValue.setText(modeLabel(accepted.executionMode()));
        renderingSwitch = true;
        backgroundOptimizationSwitch.setChecked(
                accepted.backgroundOptimizationEnabled());
        renderingSwitch = false;
    }

    private void renderRuntime(BridgeRuntimeState state) {
        if (!started) {
            return;
        }
        rootStatus.setText(rootStatusLabel(
                SettingsActivityController.rootUiState(state.rootState())));
        systemUiStatus.setText(state.systemUiConnected()
                ? R.string.status_connected
                : R.string.status_not_connected);
        systemUiBackendStatus.setText(capabilityLabel(state.systemUiCapability()));
        LastExecutionRecord lastExecution = state.lastExecution();
        if (lastExecution.isPresent()) {
            lastExecutionStatus.setText(getString(
                    R.string.last_execution_format,
                    getString(backendLabel(lastExecution.backend())),
                    lastExecution.elapsedMillis()));
        } else {
            lastExecutionStatus.setText(R.string.last_execution_none);
        }
        renderingSwitch = true;
        backgroundOptimizationSwitch.setChecked(state.backgroundOptimizationEnabled());
        renderingSwitch = false;
    }

    private void renderDisconnected() {
        if (!started) {
            return;
        }
        rootStatus.setText(R.string.status_not_connected);
        systemUiStatus.setText(R.string.status_not_connected);
    }

    private void showExecutionModeDialog() {
        ForceStopConfiguration configuration = controller.configuration();
        if (!configuration.isUsable()) {
            return;
        }
        ExecutionMode[] modes = ExecutionMode.values();
        String[] labels = new String[modes.length];
        for (int index = 0; index < modes.length; index++) {
            labels[index] = getString(modeLabel(modes[index]));
        }
        currentModeDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.execution_mode)
                .setSingleChoiceItems(
                        labels,
                        configuration.executionMode().ordinal(),
                        (dialog, which) -> {
                            submitConfiguration(controller.selectExecutionMode(modes[which]));
                            dialog.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        currentModeDialog.setOnDismissListener(ignored -> currentModeDialog = null);
        currentModeDialog.show();
    }

    private void onBackgroundOptimizationChanged(
            CompoundButton button, boolean enabled) {
        if (renderingSwitch) {
            return;
        }
        try {
            submitConfiguration(controller.setBackgroundOptimization(enabled));
        } catch (IllegalStateException unavailable) {
            renderingSwitch = true;
            button.setChecked(true);
            renderingSwitch = false;
        }
    }

    private void onLauncherIconVisibilityChanged(boolean hidden) {
        if (renderingLauncherIconSwitch) {
            return;
        }
        try {
            launcherIconController.setVisible(!hidden);
        } catch (RuntimeException failure) {
            Toast.makeText(
                    this, R.string.launcher_icon_write_failed, Toast.LENGTH_SHORT).show();
            renderLauncherIconState();
        }
    }

    private void renderLauncherIconState() {
        renderingLauncherIconSwitch = true;
        hideLauncherIconSwitch.setChecked(!launcherIconController.isVisible());
        renderingLauncherIconSwitch = false;
    }

    private void submitConfiguration(ConfigurationWrite write) {
        IForceStopBridge connectedBridge = bridge;
        int generation = lifecycleGeneration;
        if (connectedBridge == null) {
            return;
        }
        bridgeWorker.execute(() -> {
            try {
                int status = connectedBridge.updateConfiguration(
                        BridgeProtocol.VERSION,
                        write.executionMode().ordinal(),
                        write.backgroundOptimizationEnabled());
                if (protocolStatus(status) != BridgeProtocol.Status.OK) {
                    return;
                }
                BridgeConfigurationParcel parcel = connectedBridge.getModuleConfiguration(
                        BridgeProtocol.VERSION);
                if (parcel != null && isCurrentConnection(connectedBridge, generation)) {
                    ForceStopConfiguration configuration = parcel.toModel();
                    mainHandler.post(() -> renderConfiguration(configuration));
                }
            } catch (Throwable ignored) {
                mainHandler.post(this::renderDisconnected);
            }
        });
    }

    private void openBestAvailableSettings() {
        Intent[] candidates = {
                new Intent().setComponent(new ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                        .putExtra("package_name", getPackageName())
                        .putExtra("package_label", getString(R.string.app_name)),
                new Intent().setComponent(new ComponentName(
                        "com.oplus.battery",
                        "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"))
                        .putExtra("pkgName", getPackageName())
        };
        for (Intent candidate : candidates) {
            if (tryStartActivity(candidate)) {
                return;
            }
        }
        if (!tryStartActivity(new Intent(
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            startActivity(applicationDetailsIntent());
        }
    }

    private boolean tryStartActivity(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    private Intent applicationDetailsIntent() {
        return new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
    }

    private boolean isCurrentConnection(IForceStopBridge expected, int generation) {
        return started && bridge == expected && lifecycleGeneration == generation;
    }

    private static String newOpenToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static BridgeProtocol.Status protocolStatus(int ordinal) {
        BridgeProtocol.Status[] values = BridgeProtocol.Status.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : BridgeProtocol.Status.INCOMPATIBLE;
    }

    private static int rootStatusLabel(RootUiState state) {
        return switch (state) {
            case CONNECTING -> R.string.status_connecting;
            case CONNECTED -> R.string.status_connected;
            case NOT_CONNECTED -> R.string.status_not_connected;
        };
    }

    private static int capabilityLabel(SystemUiCapability capability) {
        return switch (capability) {
            case AVAILABLE -> R.string.systemui_backend_available;
            case UNAVAILABLE_UNSUPPORTED -> R.string.systemui_backend_unavailable;
            case FUSED_REJECTED -> R.string.systemui_backend_permission_rejected;
            case UNKNOWN -> R.string.status_unknown;
        };
    }

    private static int backendLabel(BackendKind backend) {
        return backend == BackendKind.ROOT
                ? R.string.backend_root
                : R.string.backend_systemui;
    }

    private static int modeLabel(ExecutionMode mode) {
        return switch (mode) {
            case AUTO -> R.string.mode_auto;
            case ROOT_FIRST -> R.string.mode_root_first;
            case SYSTEM_UI_FIRST -> R.string.mode_systemui_first;
            case ROOT_ONLY -> R.string.mode_root_only;
            case SYSTEM_UI_ONLY -> R.string.mode_systemui_only;
        };
    }
}
