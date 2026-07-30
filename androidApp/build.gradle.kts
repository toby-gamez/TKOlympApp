import java.util.Properties

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

android {
    namespace = "com.tkolymp.tkolympapp.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.tkolymp.tkolympapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 19
        versionName = "1.9"
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
    val releaseKeystorePath = localProps.getProperty("release.keystore.path")
    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = rootProject.file(releaseKeystorePath)
                storePassword = localProps.getProperty("release.keystore.password")
                    ?: error("release.keystore.password missing in local.properties")
                keyAlias = localProps.getProperty("release.key.alias")
                    ?: error("release.key.alias missing in local.properties")
                keyPassword = localProps.getProperty("release.key.password")
                    ?: error("release.key.password missing in local.properties")
            }
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
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
