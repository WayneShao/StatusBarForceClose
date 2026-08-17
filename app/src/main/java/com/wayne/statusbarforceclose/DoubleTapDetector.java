package com.wayne.statusbarforceclose;

final class DoubleTapDetector {
    static final int ACTION_DOWN = 0;
    static final int ACTION_UP = 1;
    static final int ACTION_MOVE = 2;
    static final int ACTION_CANCEL = 3;
    static final int ACTION_POINTER_DOWN = 5;

    private final float touchSlop;
    private final long maxTapDurationMs;
    private final long doubleTapTimeoutMs;

    private boolean downActive;
    private float downX;
    private float downY;
    private long downTimeMs;
    private boolean firstTapActive;
    private float firstTapX;
    private float firstTapY;
    private long firstTapUpTimeMs;

    DoubleTapDetector(float touchSlop, long maxTapDurationMs, long doubleTapTimeoutMs) {
        this.touchSlop = touchSlop;
        this.maxTapDurationMs = maxTapDurationMs;
        this.doubleTapTimeoutMs = doubleTapTimeoutMs;
    }

    boolean onEvent(int action, float rawX, float rawY, long eventTimeMs) {
        if (action == ACTION_CANCEL || action == ACTION_POINTER_DOWN) {
            reset();
            return false;
        }
        if (action == ACTION_DOWN) {
            downActive = true;
            downX = rawX;
            downY = rawY;
            downTimeMs = eventTimeMs;
            return false;
        }
        if (action == ACTION_MOVE && downActive
                && !distanceWithin(rawX, rawY, downX, downY)) {
            reset();
            return false;
        }
        if (action != ACTION_UP || !downActive) {
            return false;
        }

        downActive = false;
        boolean stationary = distanceWithin(rawX, rawY, downX, downY);
        boolean shortEnough = eventTimeMs - downTimeMs <= maxTapDurationMs;
        if (!stationary || !shortEnough) {
            firstTapActive = false;
            return false;
        }

        boolean matchesFirstTap = firstTapActive
            && eventTimeMs - firstTapUpTimeMs <= doubleTapTimeoutMs
            && distanceWithin(rawX, rawY, firstTapX, firstTapY);
        if (matchesFirstTap) {
            firstTapActive = false;
            return true;
        }

        firstTapActive = true;
        firstTapX = rawX;
        firstTapY = rawY;
        firstTapUpTimeMs = eventTimeMs;
        return false;
    }

    private boolean distanceWithin(float x1, float y1, float x2, float y2) {
        return Math.abs(x1 - x2) <= touchSlop && Math.abs(y1 - y2) <= touchSlop;
    }

    private void reset() {
        downActive = false;
        firstTapActive = false;
    }
}
