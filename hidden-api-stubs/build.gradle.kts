plugins {
    id("com.android.library")
}

android {
    namespace = "com.wayne.statusbarforceclose.hiddenapistubs"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
