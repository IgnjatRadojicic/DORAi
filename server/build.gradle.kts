plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }
}

application { mainClass = "dorai.server.ApplicationKt" }

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    implementation(libs.azure.identity)
    implementation(libs.azure.resourcemanager.resourcegraph)
    implementation(libs.azure.resourcemanager.costmanagement)
    implementation(libs.aws.resourcegroupstaggingapi)
    implementation(libs.aws.costexplorer)
    implementation(libs.aws.s3)
    implementation(libs.aws.dynamodb)
    implementation(libs.aws.lambda)
    implementation(libs.aws.sqs)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test { useJUnitPlatform() }
