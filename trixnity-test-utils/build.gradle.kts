@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    builtin(sharedLibs.plugins.android.library)
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
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
            api(sharedLibs.kotlin.test)
            api(sharedLibs.kotlinx.coroutines.test)
            api(sharedLibs.kotest.assertions.core)
            api(libs.oshai.logging)
        }

        logbackMain.dependencies {
            implementation(sharedLibs.slf4j.api)
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
    namespace = "de.connect2x.trixnity.test.utils"
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