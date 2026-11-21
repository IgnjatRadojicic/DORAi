plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(21) }

application {
    mainClass = "dorai.cli.MainKt"
    applicationName = "dorai"
}

dependencies {
    implementation(projects.shared)
    implementation(libs.clikt)
    testImplementation(libs.kotlin.test)
}

tasks.test { useJUnitPlatform() }
