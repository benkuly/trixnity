plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(sharedLibs.plugins.kotlinx.kover)
    `maven-publish`
    signing
}

kotlin {
    addJvmTarget()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.trixnityClient.clientRepositoryTest)
            }
        }
        jvmMain {
            dependencies {
                api(libs.exposed.core)

                implementation(libs.exposed.dao)
                implementation(libs.exposed.jdbc)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.h2)
                implementation(libs.logback.classic)
            }
        }
    }
}
