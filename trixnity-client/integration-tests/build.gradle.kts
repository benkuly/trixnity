import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
    jvmToolchain()
    addJvmTarget(testEnabled = (isCI && HostManager.hostIsMac).not())

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {}
        val commonTest by getting {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation(project(":trixnity-client:trixnity-client-repository-exposed"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:${Versions.ktor}")
                implementation("io.ktor:ktor-client-logging:${Versions.ktor}")
                implementation("org.testcontainers:testcontainers:${Versions.testContainers}")
                implementation("org.testcontainers:junit-jupiter:${Versions.testContainers}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
                implementation("com.h2database:h2:${Versions.h2}")
            }
        }
    }
}