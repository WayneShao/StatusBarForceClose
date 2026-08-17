plugins {
    id("com.android.application")
}

android {
    namespace = "com.wayne.statusbarforceclose"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wayne.statusbarforceclose"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "DIAGNOSTICS_ENABLED", "true")
        }
        release {
            buildConfigField("boolean", "DIAGNOSTICS_ENABLED", "false")
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}
