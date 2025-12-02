plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlinxKover)
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
                api(projects.trixnityCore)
                api(projects.trixnityCryptoCore)
                api(projects.trixnityCryptoDriver)
                api(projects.trixnityClientserverapi.trixnityClientserverapiModel)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
                implementation(projects.trixnityTestUtils)

                implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverVodozemac)
            }
        }
    }
}