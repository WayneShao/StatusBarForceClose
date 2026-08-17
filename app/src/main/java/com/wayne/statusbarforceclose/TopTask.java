package com.wayne.statusbarforceclose;

final class TopTask {
    private final String packageName;
    private final int userId;
    private final String applicationLabel;

    TopTask(String packageName, int userId, String applicationLabel) {
        this.packageName = packageName;
        this.userId = userId;
        this.applicationLabel = applicationLabel;
    }

    String packageName() {
        return packageName;
    }

    int userId() {
        return userId;
    }

    String applicationLabel() {
        return applicationLabel;
    }
}
