package com.wayne.statusbarforceclose;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public final class RootBridgeProvider extends ContentProvider {
    private static final String TAG = "StatusBarForceClose";
    private final DiagnosticLogger logger = this::report;

    @Override
    public boolean onCreate() {
        logger.info("root_bridge_created", "process=" + android.os.Process.myPid());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        String callingPackage = getCallingPackage();
        if (!RootBridgeCallerPolicy.isAllowed(callingPackage)) {
            logger.warn("root_bridge_caller_rejected", "package=" + callingPackage);
            throw new SecurityException("Root bridge caller is not SystemUI");
        }
        if (!RootBridgeProtocol.METHOD_FORCE_STOP.equals(method) || extras == null) {
            throw new IllegalArgumentException("Unsupported root bridge call");
        }

        String packageName = extras.getString(RootBridgeProtocol.EXTRA_PACKAGE);
        int userId = extras.getInt(RootBridgeProtocol.EXTRA_USER_ID, -1);
        boolean success = new SuForceStopMethod(logger).forceStop(packageName, userId);
        Bundle result = new Bundle();
        result.putBoolean(RootBridgeProtocol.RESULT_SUCCESS, success);
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private void report(int priority, String event, String details, Throwable throwable) {
        if (!BuildConfig.DIAGNOSTICS_ENABLED) {
            return;
        }
        String message = "event=" + event + " " + details;
        if (throwable == null) {
            Log.println(priority, TAG, message);
        } else {
            Log.println(priority, TAG, message + "\n" + Log.getStackTraceString(throwable));
        }
    }
}
