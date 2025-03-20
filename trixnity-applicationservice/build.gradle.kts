plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlinxKover)
    trixnity.general
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
                api(projects.trixnityClientserverapi.trixnityClientserverapiClient)
                api(projects.trixnityApplicationserviceapi.trixnityApplicationserviceapiServer)

                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.statusPages)
                implementation(libs.ktor.server.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testUtils)

                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
                implementation(libs.ktor.server.testHost)
            }
        }
    }
}