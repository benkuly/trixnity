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

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        commonMain.dependencies {
            api(sharedLibs.kotlin.test)
            api(sharedLibs.kotlinx.coroutines.test)
            api(sharedLibs.kotest.assertions.core)
            api(sharedLibs.lognity.api)
            implementation(sharedLibs.lognity.core)
            implementation(sharedLibs.lognity.test)
        }

        jvmMain.dependencies {
            api(kotlin("test-junit5"))
            implementation(sharedLibs.lognity.slf4j)
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