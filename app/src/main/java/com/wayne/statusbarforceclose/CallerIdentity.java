package com.wayne.statusbarforceclose;

import java.util.Arrays;

record CallerIdentity(int uid, String[] packages) {
    CallerIdentity {
        packages = packages == null ? null : Arrays.copyOf(packages, packages.length);
    }

    @Override
    public String[] packages() {
        return packages == null ? null : Arrays.copyOf(packages, packages.length);
    }
}
