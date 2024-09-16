plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    trixnity.general
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
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
                implementation(libs.kotest.runner.junit5)
                implementation(libs.h2)
                implementation(libs.logback.classic)
            }
        }
    }
}
