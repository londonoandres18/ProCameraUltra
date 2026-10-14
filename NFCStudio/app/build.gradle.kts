plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nfcstudio.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nfcstudio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    kotlinOptions { jvmTarget = "17" }
}
