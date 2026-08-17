package com.wayne.statusbarforceclose;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class SuForceStopMethod implements ForceStopMethod {
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9._]+");
    private static final long TIMEOUT_SECONDS = 2L;

    @Override
    public boolean forceStop(String packageName, int userId) {
        if (packageName == null || !PACKAGE_NAME.matcher(packageName).matches() || userId < 0) {
            return false;
        }

        Process process = null;
        try {
            String command = "am force-stop --user " + userId + " " + packageName
                    + " >/dev/null 2>&1";
            process = new ProcessBuilder("su", "-c", command).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Throwable ignored) {
            if (process != null) {
                process.destroyForcibly();
            }
            return false;
        }
    }
}
