plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                api(projects.trixnityTestUtils)
            }
        }
    }
}