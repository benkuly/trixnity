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
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir, browserEnabled = false)
    val nativeTargets = addDefaultNativeTargetsWhenEnabled()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")

                api("com.squareup.okio:okio:${Versions.okio}")
                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        jsTarget?.mainSourceSet(this) {
            dependencies {
                implementation("com.squareup.okio:okio-nodefilesystem:${Versions.okio}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("com.squareup.okio:okio-fakefilesystem:${Versions.okio}")
            }
        }
        jvmTarget?.testSourceSet(this) {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}
