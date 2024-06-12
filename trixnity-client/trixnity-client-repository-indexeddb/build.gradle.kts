plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotest)
}

kotlin {
    jvmToolchain()
    addJsTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        jsMain {
            dependencies {
                implementation(project(":trixnity-client"))

                implementation(libs.oshai.logging)

                api(libs.juulLabs.indexeddb)
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":trixnity-client:client-repository-test"))
            }
        }
    }
}
