plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")

    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJsTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        jsMain {
            dependencies {
                implementation(projects.trixnityClient)
                implementation(projects.trixnityIdbUtils)
            }
        }
        jsTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.idbSchemaexporter)
                implementation(libs.kotest.assertions.json)
            }
        }
    }
}
