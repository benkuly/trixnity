@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain()
    addJsTarget(rootDir, nodeJsEnabled = false)

    // TODO: add wasm helper in buildSrc
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(libs.kotlin.wrappers.bom))
            implementation(libs.kotlin.wrappers.browser)
        }
    }
}
