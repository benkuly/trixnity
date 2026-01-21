plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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
