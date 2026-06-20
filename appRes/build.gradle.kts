plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.tkolymp.tkolympapp.res"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
