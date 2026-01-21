@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJsTarget(rootDir, nodeJsEnabled = false)

    // TODO: add wasm helper in buildSrc
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            api(sharedLibs.kotlin.browser)
            api(sharedLibs.kotlinx.coroutines.core)
        }
    }
}
