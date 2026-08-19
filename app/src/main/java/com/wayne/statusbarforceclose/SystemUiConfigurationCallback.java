package com.wayne.statusbarforceclose;

@FunctionalInterface
interface SystemUiConfigurationCallback {
    void onConfiguration(ForceStopConfiguration configuration);
}
