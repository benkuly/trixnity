@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addJsTarget(rootDir)
    addAndroidTarget()
    addNativeDesktopTargets()
    addNativeAppleTargets()

    applyDefaultHierarchyTemplate {
        common {
            group("logback") {
                withAndroidTarget()
                withJvm()
            }

            group("direct") {
                group("linux")
                group("mingw")
                group("web") { withJs() }
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        val logbackMain by getting

        commonMain.dependencies {
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
            api(libs.kotest.assertions.core)
            api(libs.oshai.logging)
        }

        logbackMain.dependencies {
            implementation(libs.slf4j.api)
            implementation(libs.logback.classic)
        }

        jvmMain.dependencies {
            api(kotlin("test-junit5"))
        }

        androidMain.dependencies {
            api(kotlin("test-junit"))
        }
    }
}

android {
    namespace = "net.folivo.trixnity.test.utils"
    compileSdk = libs.versions.androidTargetSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
        }
    }
}