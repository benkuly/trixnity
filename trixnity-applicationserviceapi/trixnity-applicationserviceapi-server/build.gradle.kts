plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlinxKover)
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    linuxX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                api(projects.trixnityApiServer)
                api(projects.trixnityApplicationserviceapi.trixnityApplicationserviceapiModel)

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.contentNegotiation)
                implementation(libs.ktor.server.resources)
                implementation(libs.ktor.server.auth)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.server.testHost)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}