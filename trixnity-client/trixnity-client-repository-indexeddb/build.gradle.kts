plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotest)
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

                implementation(libs.oshai.logging)

                api(libs.juulLabs.indexeddb)
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.trixnityClient.clientRepositoryTest)
            }
        }
    }
}
