import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")
    android {
        namespace = "dorai.app"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.datastore.preferences)
        }
        named("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.java.keyring)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dorai.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "DORAi"
            packageVersion = project.version.toString()
            // Fixed for the life of the product: it lets a new MSI upgrade an older install in place.
            windows { upgradeUuid = "8f23d603-e143-41e8-901e-1074dce8fd1c" }
            // Signed and notarized only when the release workflow has Apple's certificate (see docs/plan-releases.md).
            macOS {
                bundleID = "dorai.desktop"
                System.getenv("DORAI_MACOS_SIGN_IDENTITY")?.takeIf { it.isNotBlank() }?.let { id ->
                    signing {
                        sign.set(true)
                        identity.set(id)
                    }
                    notarization {
                        appleID.set(System.getenv("DORAI_MACOS_NOTARY_APPLE_ID"))
                        password.set(System.getenv("DORAI_MACOS_NOTARY_PASSWORD"))
                        teamID.set(System.getenv("DORAI_MACOS_TEAM_ID"))
                    }
                }
            }
        }
    }
}
