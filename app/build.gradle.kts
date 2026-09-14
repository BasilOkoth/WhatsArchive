plugins {
    id("com.android.application")
}

android {
    namespace = "com.basil.whatsarchive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.basil.whatsarchive"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.2.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation("androidx.biometric:biometric:1.1.0")
}
