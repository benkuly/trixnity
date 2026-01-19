plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                api(projects.trixnityClientserverapi.trixnityClientserverapiClient)
                api(projects.trixnityCrypto)

                api(libs.koin.core)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.ktorTestUtils)
                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverVodozemac)
                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverLibolm)

                implementation(libs.ktor.client.mock)
                implementation(libs.kotest.common)
            }
        }
    }
}
