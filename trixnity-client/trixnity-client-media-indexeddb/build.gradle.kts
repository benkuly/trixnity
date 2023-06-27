import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
    jvmToolchain()
    ciDummyTarget()
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")

                implementation("io.github.oshai:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        jsTarget?.mainSourceSet(this) {
            dependencies {
                api("com.juul.indexeddb:core:${Versions.juulLabsIndexeddb}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
            }
        }
    }
}
