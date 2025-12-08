plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    trixnity.publish
}

kotlin {
    jvmToolchain()
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
                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverLibolm)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.ktorTestUtils)

                implementation(libs.ktor.client.mock)
                implementation(libs.kotest.common)
            }
        }
    }
}
