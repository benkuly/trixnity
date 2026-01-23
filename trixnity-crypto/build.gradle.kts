plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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
                api(projects.trixnityCore)
                api(projects.trixnityCryptoCore)
                api(projects.trixnityCryptoDriver)
                api(projects.trixnityClientserverapi.trixnityClientserverapiModel)

                implementation(libs.lognity.api)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)

                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverVodozemac)
            }
        }
    }
}