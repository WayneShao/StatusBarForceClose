package com.wayne.statusbarforceclose;

final class SettingsIntentSelector {
    private SettingsIntentSelector() {
    }

    static SettingsDestination select(
            boolean vendorBackgroundResolvable,
            boolean batteryOptimizationResolvable) {
        if (vendorBackgroundResolvable) {
            return SettingsDestination.VENDOR_BACKGROUND;
        }
        return batteryOptimizationResolvable
                ? SettingsDestination.BATTERY_OPTIMIZATION
                : SettingsDestination.APPLICATION_DETAILS;
    }
}
