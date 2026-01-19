plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
    signing
}

kotlin {
    addJvmTarget()
    addNativeTargets()
    addJsTarget(rootDir)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        val commonMain by getting

        commonMain.dependencies {
            implementation(projects.trixnityClient)
            implementation(projects.trixnityVodozemac)
            implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverVodozemac)
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}