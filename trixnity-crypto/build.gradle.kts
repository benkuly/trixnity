plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
}

kotlin {
    addJvmTarget()
    addWebTarget(rootDir)
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

                implementation(sharedLibs.lognity.api)
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