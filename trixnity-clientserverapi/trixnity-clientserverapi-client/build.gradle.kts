plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                api(project(":trixnity-api-client"))
                api(project(":trixnity-clientserverapi:trixnity-clientserverapi-model"))

                implementation(libs.kotlinx.datetime)

                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.client.resources)

                implementation(libs.benasher44.uuid)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":test-utils"))

                implementation(libs.ktor.client.mock)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}