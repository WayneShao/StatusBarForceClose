package com.wayne.statusbarforceclose;

import android.os.SystemClock;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Release diagnostics: no package names, labels, session tokens or exception messages. */
final class HealthLog {
    private static final Map<String, Long> last = new HashMap<>();
    private static final Set<String> transitions = Set.of("hook_installed", "double_tap_detected",
            "force_stop_result", "request_released", "systemui_bridge_ready",
            "libsu_root_connected");

    static synchronized void record(int priority, String event, Throwable failure) {
        if (priority < Log.WARN && !transitions.contains(event)) return;
        long now = SystemClock.elapsedRealtime();
        Long previous = last.get(event);
        if (previous != null && now - previous < 5_000L) return;
        if (last.size() >= 64) last.clear();
        last.put(event, now);
        Log.println(priority, "StatusBarForceClose", "health=" + event
                + (failure == null ? "" : " error=" + failure.getClass().getSimpleName()));
    }
}
