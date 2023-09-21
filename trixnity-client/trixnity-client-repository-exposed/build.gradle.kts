plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                implementation(project(":trixnity-client"))

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":trixnity-client:client-repository-test"))
                implementation(libs.benasher44.uuid)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.exposed.core)

                implementation(libs.exposed.dao)
                implementation(libs.exposed.jdbc)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.h2)
                implementation(libs.logback.classic)
            }
        }
    }
}
