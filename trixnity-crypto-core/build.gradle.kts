import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.kotest.multiplatform")
    // id("com.louiscad.complete-kotlin") version Versions.completeKotlinPlugin // only enable locally for code completion
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
    jvmToolchain()
    val jvmTarget = addDefaultJvmTargetWhenEnabled()
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir)
    val nativeTargets = addDefaultNativeTargetsWhenEnabled()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-utils"))
                implementation("com.soywiz.korlibs.krypto:krypto:${Versions.korlibs}") // TODO into test only

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        nativeTargets.filter { it.targetName.contains("linux") }.forEach { target ->
            target.compilations {
                "main" {
                    cinterops {
                        val libRandom by creating {
                            defFile("src/linuxMain/cinterop/librandom.def")
                            packageName("net.folivo.trixnity.crypto.core.cinterop")
                        }
                    }
                }
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
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