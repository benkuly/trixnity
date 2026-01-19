plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(sharedLibs.plugins.kotlinx.kover)
    `maven-publish`
    signing
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

                implementation(libs.oshai.logging)
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