plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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
                implementation(sharedLibs.kotlin.test)
                implementation(sharedLibs.kotlinx.coroutines.test)
                implementation(sharedLibs.kotest.assertions.core)
            }
        }
    }
}