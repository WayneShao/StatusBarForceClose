package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AndroidUserIdsTest {
    @Test
    public void mapsPrimaryUserUidToUserZero() {
        assertEquals(0, AndroidUserIds.fromUid(10_044));
    }

    @Test
    public void mapsSecondaryUserUidToItsUserId() {
        assertEquals(10, AndroidUserIds.fromUid(1_010_044));
    }

    @Test
    public void rejectsNegativeUids() {
        assertEquals(-1, AndroidUserIds.fromUid(-1));
    }
}
