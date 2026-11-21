import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvm()
    android {
        namespace = "dorai.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain.dependencies {
            // Exposed as api: DTOs use these types and DoraiApi takes an HttpClient.
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            // A self-signed certificate for the pinning test, built the way the server builds its own.
            implementation(libs.ktor.network.tls.certificates)
        }
    }
}

// Generates dorai.shared.DoraiBuild with the product version, so the server, the apps and the CLI
// know at runtime which version they are.
val doraiVersion = project.version.toString()
val doraiBuildDir = layout.buildDirectory.dir("generated/doraiBuild/kotlin")
val generateDoraiBuild = tasks.register("generateDoraiBuild") {
    inputs.property("version", doraiVersion)
    outputs.dir(doraiBuildDir)
    doLast {
        val file = doraiBuildDir.get().file("dorai/shared/DoraiBuild.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package dorai.shared
            |
            |/** Generated from dorai.version in gradle.properties. Do not edit. */
            |object DoraiBuild {
            |    const val VERSION = "$doraiVersion"
            |}
            |""".trimMargin(),
        )
    }
}
kotlin.sourceSets.commonMain { kotlin.srcDir(generateDoraiBuild) }
