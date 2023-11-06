import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain()
    addJvmTarget(testEnabled = (isCI && HostManager.hostIsMac).not())

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
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.ktor.client.java)
                implementation(libs.ktor.client.logging)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.logback.classic)
                implementation(libs.h2)
            }
        }
    }
}