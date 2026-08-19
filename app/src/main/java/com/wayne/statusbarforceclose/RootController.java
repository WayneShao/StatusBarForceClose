package com.wayne.statusbarforceclose;

interface RootController {
    boolean isAlive();

    boolean forceStop(String packageName, int userId) throws Exception;
}
