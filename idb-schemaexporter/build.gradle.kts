@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addWebTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            implementation(sharedLibs.kotlin.browser)
        }
    }
}
