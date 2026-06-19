import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleServices)
}

android {
    namespace = "com.tkolymp.tkolympapp.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tkolymp.tkolympapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 18
        versionName = "1.8"
        buildConfigField("String", "API_BASE_URL", "\"${localProps["api.base.url"] ?: ""}\"")
        buildConfigField("String", "TENANT_ID", "\"${localProps["tenant.id"] ?: ""}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.appRes)
    implementation(projects.shared)
    implementation(libs.kotlinx.coroutines.android)
}
