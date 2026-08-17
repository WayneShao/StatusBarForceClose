package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DoubleTapDetectorTest {
    @Test
    public void triggersAfterTwoStationaryTapsWithinTimeout() {
        DoubleTapDetector detector = new DoubleTapDetector(20f, 250L, 250L);

        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_000L));
        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_UP, 100f, 20f, 1_050L));
        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_DOWN, 104f, 22f, 1_150L));
        assertTrue(detector.onEvent(DoubleTapDetector.ACTION_UP, 104f, 22f, 1_200L));
    }

    @Test
    public void rejectsASecondTapAfterTimeout() {
        DoubleTapDetector detector = new DoubleTapDetector(20f, 250L, 250L);

        detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_000L);
        detector.onEvent(DoubleTapDetector.ACTION_UP, 100f, 20f, 1_050L);
        detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_400L);

        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_UP, 100f, 20f, 1_450L));
    }

    @Test
    public void rejectsDragAndCancelSequences() {
        DoubleTapDetector detector = new DoubleTapDetector(20f, 250L, 250L);

        detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_000L);
        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_UP, 150f, 20f, 1_050L));
        detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_100L);
        detector.onEvent(DoubleTapDetector.ACTION_CANCEL, 100f, 20f, 1_120L);
        detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_180L);

        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_UP, 100f, 20f, 1_220L));
    }

    @Test
    public void rejectsGestureThatMovesAwayThenReturnsBeforeUp() {
        DoubleTapDetector detector = new DoubleTapDetector(20f, 250L, 250L);

        detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_000L);
        detector.onEvent(DoubleTapDetector.ACTION_MOVE, 150f, 20f, 1_020L);
        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_UP, 100f, 20f, 1_050L));
    }

    @Test
    public void rejectsMultiTouchGesture() {
        DoubleTapDetector detector = new DoubleTapDetector(20f, 250L, 250L);

        detector.onEvent(DoubleTapDetector.ACTION_DOWN, 100f, 20f, 1_000L);
        detector.onEvent(DoubleTapDetector.ACTION_POINTER_DOWN, 110f, 20f, 1_020L);
        assertFalse(detector.onEvent(DoubleTapDetector.ACTION_UP, 100f, 20f, 1_050L));
    }
}
