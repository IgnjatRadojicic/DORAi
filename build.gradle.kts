plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// One version for the whole product, from gradle.properties. The limits come from the platforms:
// macOS refuses a 0 major, Windows MSI allows at most 255, and Android's versionCode
// (major * 10000 + minor * 100 + patch) only always goes up while minor and patch stay below 100.
val doraiVersion = providers.gradleProperty("dorai.version").get()
val doraiVersionParts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(doraiVersion)
    ?.groupValues?.drop(1)?.map(String::toInt)
    ?: error("dorai.version must be X.Y.Z, got '$doraiVersion'")
val (doraiMajor, doraiMinor, doraiPatch) = doraiVersionParts
require(doraiMajor in 1..255 && doraiMinor in 0..99 && doraiPatch in 0..99) {
    "dorai.version $doraiVersion is out of range: major 1..255, minor and patch 0..99"
}

allprojects {
    version = doraiVersion
    extra["doraiVersionCode"] = doraiMajor * 10000 + doraiMinor * 100 + doraiPatch
}
