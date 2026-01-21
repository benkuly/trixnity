plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
}

kotlin {
    addJsTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        jsMain {
            dependencies {
                implementation(projects.trixnityClient)

                api(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
                api(libs.kotlin.wrappers.browser)
                implementation(libs.oshai.logging)
            }
        }
        jsTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}
