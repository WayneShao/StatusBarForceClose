package com.wayne.statusbarforceclose;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Bounds every SystemUI -> bridge transaction, including registration and diagnostics. */
final class TimedBridge {
    static final long QUERY_TIMEOUT_MS = 2_000L;
    static final long EXECUTION_TIMEOUT_MS = 4_000L;

    private TimedBridge() { }

    static IForceStopBridge wrap(IForceStopBridge remote, BoundedRemoteCalls calls) {
        return (IForceStopBridge) Proxy.newProxyInstance(
                IForceStopBridge.class.getClassLoader(),
                new Class<?>[] {IForceStopBridge.class}, (proxy, method, args) -> {
                    if (method.getName().equals("asBinder")) return remote.asBinder();
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            default -> "TimedBridge";
                        };
                    }
                    try {
                        return calls.call(() -> {
                            try { return method.invoke(remote, args); }
                            catch (InvocationTargetException wrapped) {
                                if (wrapped.getCause() instanceof Exception error) throw error;
                                throw new IllegalStateException(wrapped.getCause());
                            }
                        }, method.getName().equals("forceStopRoot")
                                ? EXECUTION_TIMEOUT_MS : QUERY_TIMEOUT_MS);
                    } catch (Exception failure) {
                        // AIDL declares RemoteException; keep failures inside that contract.
                        throw new android.os.RemoteException("Bridge " + method.getName()
                                + ": " + failure.getClass().getSimpleName());
                    }
                });
    }
}
