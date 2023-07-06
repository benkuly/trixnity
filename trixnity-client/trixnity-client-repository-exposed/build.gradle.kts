import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
    jvmToolchain()
    val jvmTarget = addDefaultJvmTargetWhenEnabled()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")

                implementation("io.github.oshai:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":trixnity-client:client-repository-test"))
                implementation("com.benasher44:uuid:${Versions.uuid}")
            }
        }
        jvmTarget?.mainSourceSet(this) {
            dependencies {
                api("org.jetbrains.exposed:exposed-core:${Versions.exposed}")

                implementation("org.jetbrains.exposed:exposed-dao:${Versions.exposed}")
                implementation("org.jetbrains.exposed:exposed-jdbc:${Versions.exposed}")
            }
        }
        jvmTarget?.testSourceSet(this) {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("com.h2database:h2:${Versions.h2}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}
