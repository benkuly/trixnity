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
                api(projects.trixnityClientserverapi.trixnityClientserverapiClient)
                api(projects.trixnityCrypto)

                api(sharedLibs.koin.core)

                implementation(sharedLibs.lognity.api)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.ktorTestUtils)
                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverVodozemac)
                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverLibolm)

                implementation(sharedLibs.ktor.client.mock)
                implementation(sharedLibs.kotest.common)
            }
        }
    }
}
