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
        }
        commonMain {
            dependencies {
                api(projects.trixnityCore)

                implementation(sharedLibs.ktor.resources)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}