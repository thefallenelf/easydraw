plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.zhangchuang.nianchu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhangchuang.nianchu"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.9.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file("E:/apk-build-tmp/keystore/nianchu-release.jks")
            storePassword = "ZcCreative2026@nianchu"
            keyAlias = "nianchu"
            keyPassword = "ZcCreative2026@nianchu"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.recyclerview)
}