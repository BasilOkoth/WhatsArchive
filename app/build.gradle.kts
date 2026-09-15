plugins {
    id("com.android.application")
}

android {
    namespace = "com.elimara.chatarchive"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.elimara.chatarchive"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "0.6.0"
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
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("com.android.billingclient:billing:9.1.0")
}
