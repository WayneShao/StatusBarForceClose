package com.wayne.statusbarforceclose;

import android.content.Context;
import android.os.Bundle;

final class RootBridgeForceStopMethod implements ForceStopMethod {
    private final Context context;
    private final DiagnosticLogger logger;

    RootBridgeForceStopMethod(Context context, DiagnosticLogger logger) {
        this.context = context;
        this.logger = logger;
    }

    @Override
    public boolean forceStop(String packageName, int userId) {
        if (packageName == null || userId < 0) {
            logger.warn("root_bridge_invalid_target", "package=" + packageName
                    + " user=" + userId);
            return false;
        }

        try {
            logger.info("root_bridge_attempt", "package=" + packageName + " user=" + userId);
            Bundle extras = new Bundle();
            extras.putString(RootBridgeProtocol.EXTRA_PACKAGE, packageName);
            extras.putInt(RootBridgeProtocol.EXTRA_USER_ID, userId);
            Bundle result = context.getContentResolver().call(
                    RootBridgeProtocol.URI,
                    RootBridgeProtocol.METHOD_FORCE_STOP,
                    null,
                    extras);
            boolean success = result != null
                    && result.getBoolean(RootBridgeProtocol.RESULT_SUCCESS, false);
            logger.log(
                    success ? android.util.Log.INFO : android.util.Log.WARN,
                    "root_bridge_result",
                    "package=" + packageName + " user=" + userId + " success=" + success,
                    null);
            return success;
        } catch (Throwable throwable) {
            logger.error("root_bridge_exception", "package=" + packageName
                    + " user=" + userId, throwable);
            return false;
        }
    }
}
