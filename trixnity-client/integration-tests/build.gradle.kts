import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain()
    addJvmTarget(testEnabled = (isCI && HostManager.hostIsMac).not()) {
        maxHeapSize = "8g"
        maxParallelForks = if (isCI) 3 else 1
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {}
        commonTest {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation(project(":trixnity-client:trixnity-client-repository-exposed"))
                implementation(project(":trixnity-client:trixnity-client-repository-realm"))
                implementation(kotlin("test"))
                implementation(libs.kotest.common)
                implementation(libs.kotest.assertions.core)
                implementation(libs.benasher44.uuid)
                implementation(libs.oshai.logging)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.ktor.client.logging)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.h2)
                implementation(libs.postgresql)
                implementation(libs.hikari)
                implementation(libs.logback.classic)
            }
        }
    }
}