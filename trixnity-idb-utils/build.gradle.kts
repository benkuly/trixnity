@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
}

kotlin {
    addJsTarget(rootDir, nodeJsEnabled = false)

    // TODO: add wasm helper in buildSrc
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            api(libs.kotlin.wrappers.browser)
            api(sharedLibs.kotlinx.coroutines.core)
        }
    }
}
