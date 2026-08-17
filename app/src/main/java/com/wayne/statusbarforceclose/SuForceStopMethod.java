package com.wayne.statusbarforceclose;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class SuForceStopMethod implements ForceStopMethod {
    private static final Pattern PACKAGE_NAME = Pattern.compile("[A-Za-z0-9._]+");
    private static final long TIMEOUT_SECONDS = 2L;
    private final DiagnosticLogger logger;

    SuForceStopMethod(DiagnosticLogger logger) {
        this.logger = logger;
    }

    @Override
    public boolean forceStop(String packageName, int userId) {
        if (packageName == null || !PACKAGE_NAME.matcher(packageName).matches() || userId < 0) {
            logger.warn("su_invalid_target", "package=" + packageName + " user=" + userId);
            return false;
        }

        Process process = null;
        try {
            logger.info("su_attempt", "package=" + packageName + " user=" + userId);
            String command = "am force-stop --user " + userId + " " + packageName
                    + " >/dev/null 2>&1";
            process = new ProcessBuilder("su", "-c", command).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.warn("su_timeout", "package=" + packageName + " user=" + userId
                        + " timeoutSeconds=" + TIMEOUT_SECONDS);
                return false;
            }
            int exitCode = process.exitValue();
            logger.log(
                    exitCode == 0 ? android.util.Log.INFO : android.util.Log.WARN,
                    "su_exit",
                    "package=" + packageName + " user=" + userId + " exitCode=" + exitCode,
                    null);
            return exitCode == 0;
        } catch (Throwable throwable) {
            if (process != null) {
                process.destroyForcibly();
            }
            logger.error("su_exception", "package=" + packageName + " user=" + userId,
                    throwable);
            return false;
        }
    }
}
