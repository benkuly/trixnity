plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain()
    addJsTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val jsMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))

                api(libs.juulLabs.indexeddb)
                implementation(libs.oshai.logging)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
                implementation(libs.benasher44.uuid)
            }
        }
    }
}
