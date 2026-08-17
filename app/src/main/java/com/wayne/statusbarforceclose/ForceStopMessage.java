package com.wayne.statusbarforceclose;

final class ForceStopMessage {
    private ForceStopMessage() {
    }

    static String success(String applicationLabel, String packageName) {
        String displayName = applicationLabel == null || applicationLabel.isBlank()
                ? packageName
                : applicationLabel.trim();
        return "已强制关闭" + displayName;
    }
}
