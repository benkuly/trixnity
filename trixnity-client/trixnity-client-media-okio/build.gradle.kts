plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(sharedLibs.plugins.kotlinx.kover)
    `maven-publish`
    signing
}

kotlin {
    addJvmTarget()
    addJsTarget(rootDir, browserEnabled = false)
    addNativeTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                api(libs.okio)
                implementation(libs.oshai.logging)
            }
        }
        jsMain {
            dependencies {
                implementation(libs.okio.nodefilesystem)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(libs.okio.fakefilesystem)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}
