package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class SuForceStopMethodTest {
    @Test
    public void usesAbsoluteSuPath() {
        assertArrayEquals(
                new String[] {
                        "/system/bin/su",
                        "-c",
                        "am force-stop --user 999 com.example.reader >/dev/null 2>&1"
                },
                SuForceStopMethod.buildProcessArguments("com.example.reader", 999));
    }
}
