package com.wayne.statusbarforceclose;

final class ProcessRegistrationGate {
    private boolean started;

    synchronized boolean tryStart() {
        if (started) {
            return false;
        }
        started = true;
        return true;
    }
}
