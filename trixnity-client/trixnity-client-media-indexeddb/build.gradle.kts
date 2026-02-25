plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
}

kotlin {
    addWebTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.js.ExperimentalWasmJsInterop")
        }
        webMain {
            dependencies {
                implementation(projects.trixnityClient)
                implementation(projects.trixnityIdbUtils)
            }
        }
        webTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.idbSchemaexporter)
                implementation(sharedLibs.kotest.assertions.json)
            }
        }
    }
}
