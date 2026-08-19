package com.wayne.statusbarforceclose;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.topjohnwu.superuser.ipc.RootService;
import com.topjohnwu.superuser.Shell;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class LibsuRootBindAdapter implements RootBindAdapter {
    private final Context context;
    private final DiagnosticLogger logger;

    LibsuRootBindAdapter(Context context, DiagnosticLogger logger) {
        this.context = Objects.requireNonNull(context, "context");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Binding bind(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        Boolean granted = Shell.isAppGrantedRoot();
        if (Boolean.FALSE.equals(granted)) {
            logger.warn("libsu_root_denied", "source=Shell.isAppGrantedRoot");
            listener.denied();
            return () -> { };
        }
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                IRootActivityController controller = IRootActivityController.Stub.asInterface(service);
                logger.info("libsu_root_connected", "component=" + name.flattenToShortString()
                        + " binder=" + (controller != null));
                listener.connected(controller == null ? null : new AidlRootController(controller));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                logger.warn("libsu_root_disconnected", "component="
                        + name.flattenToShortString());
                listener.disconnected();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                logger.warn("libsu_root_null_binding", "component="
                        + name.flattenToShortString());
                listener.incompatible();
            }

            @Override
            public void onBindingDied(ComponentName name) {
                logger.warn("libsu_root_binding_died", "component="
                        + name.flattenToShortString());
                listener.failed(new IllegalStateException("RootService binding died"));
            }
        };
        LibsuBinding binding = new LibsuBinding(connection);
        logger.info("libsu_root_bind_start", "service="
                + RootActivityManagerService.class.getName());
        RootService.bind(new Intent(context, RootActivityManagerService.class), connection);
        return binding;
    }

    private final class LibsuBinding implements Binding {
        private final ServiceConnection connection;
        private final AtomicBoolean unbound = new AtomicBoolean();

        LibsuBinding(ServiceConnection connection) {
            this.connection = connection;
        }

        @Override
        public void unbind() {
            if (!unbound.compareAndSet(false, true)) {
                return;
            }
            try {
                RootService.unbind(connection);
                logger.info("libsu_root_unbound", "service="
                        + RootActivityManagerService.class.getName());
            } catch (RuntimeException failure) {
                logger.error("libsu_root_unbind_failed", "service="
                        + RootActivityManagerService.class.getName(), failure);
            }
        }
    }

    private static final class AidlRootController implements RootController {
        private final IRootActivityController delegate;

        AidlRootController(IRootActivityController delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isAlive() {
            return delegate.asBinder().isBinderAlive();
        }

        @Override
        public boolean forceStop(String packageName, int userId) throws Exception {
            return delegate.forceStop(packageName, userId);
        }
    }
}
