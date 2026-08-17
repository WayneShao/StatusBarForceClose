package com.wayne.statusbarforceclose;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ForceStopMessageTest {
    @Test
    public void usesApplicationLabelWhenAvailable() {
        assertEquals("已强制关闭微信", ForceStopMessage.success("微信", "com.tencent.mm"));
    }

    @Test
    public void fallsBackToPackageNameWhenLabelIsMissing() {
        assertEquals(
                "已强制关闭com.example.reader",
                ForceStopMessage.success("  ", "com.example.reader"));
    }
}
