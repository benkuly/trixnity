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

                api(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
                api(sharedLibs.kotlin.browser)
                implementation(sharedLibs.lognity.api)
            }
        }
        webTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}
