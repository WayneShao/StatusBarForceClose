package com.wayne.statusbarforceclose;

final class AndroidUserIds {
    private static final int PER_USER_RANGE = 100_000;

    private AndroidUserIds() {
    }

    static int fromUid(int uid) {
        return uid < 0 ? -1 : uid / PER_USER_RANGE;
    }
}
