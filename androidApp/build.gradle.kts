plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dorai.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dorai.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // From dorai.version in gradle.properties; the root build works out the code.
        versionCode = project.extra["doraiVersionCode"] as Int
        versionName = project.version.toString()
    }

    // The release workflow signs with the one release key through DORAI_ANDROID_* variables.
    // Without them (local builds) the release APK stays unsigned; debug builds always use the debug key.
    val releaseKeystore = providers.environmentVariable("DORAI_ANDROID_KEYSTORE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("DORAI_ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("DORAI_ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("DORAI_ANDROID_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.app)
    implementation(libs.androidx.activity.compose)
}
