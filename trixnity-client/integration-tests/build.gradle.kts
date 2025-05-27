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
                implementation(projects.trixnityClient)
                implementation(projects.trixnityClient.trixnityClientRepositoryExposed)
                implementation(projects.trixnityClient.trixnityClientRepositoryRoom)
                implementation(libs.androidx.sqlite.bundled)
                implementation(kotlin("test"))
                implementation(libs.kotest.common)
                implementation(libs.kotest.assertions.core)
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
                implementation(libs.openjdk.jol)
            }
        }
    }
}