@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation

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
           group("nonAndroid"){
               withCompilations {
                   it !is KotlinJvmAndroidCompilation
               }
           }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        commonMain.dependencies {
            api(sharedLibs.kotlin.test)
            api(sharedLibs.kotlinx.coroutines.test)
            api(sharedLibs.kotest.assertions.core)
            api(libs.lognity.api)
            implementation(libs.lognity.core)
        }

        jvmMain.dependencies {
            api(kotlin("test-junit5"))
            implementation(libs.lognity.slf4j)
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