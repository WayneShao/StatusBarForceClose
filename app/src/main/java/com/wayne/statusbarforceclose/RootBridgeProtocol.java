package com.wayne.statusbarforceclose;

import android.net.Uri;

final class RootBridgeProtocol {
    static final String AUTHORITY = "com.wayne.statusbarforceclose.root";
    static final Uri URI = Uri.parse("content://" + AUTHORITY);
    static final String METHOD_FORCE_STOP = "forceStop";
    static final String EXTRA_PACKAGE = "package";
    static final String EXTRA_USER_ID = "userId";
    static final String RESULT_SUCCESS = "success";

    private RootBridgeProtocol() {
    }
}
