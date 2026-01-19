@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
}

kotlin {
    addJsTarget(rootDir, nodeJsEnabled = false)

    // TODO: add wasm helper in buildSrc
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            implementation(libs.kotlin.wrappers.browser)
        }
    }
}
