plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    trixnity.general
    trixnity.publish
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
                implementation(projects.trixnityClient)

                api(libs.juulLabs.indexeddb)
                implementation(libs.oshai.logging)
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}
