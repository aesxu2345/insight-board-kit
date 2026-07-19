plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.uikitinsightdemo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.uikitinsightdemo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation("org.mozilla.geckoview:geckoview:152.0.20260713164047")
    implementation(files("../../UIKitInsight/build/outputs/aar/UIKitInsight-release.aar"))
}
