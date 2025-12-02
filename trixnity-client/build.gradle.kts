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
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.ktorTestUtils)
                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverVodozemac)

                implementation(libs.ktor.client.mock)

                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.common)
                implementation(libs.kotest.assertions.core)
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}
