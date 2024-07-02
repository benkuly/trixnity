plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.mokkery)
}

kotlin {
    jvmToolchain()
    addJvmTarget(useJUnitPlatform = false)
    linuxX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                api(project(":trixnity-api-server"))
                api(project(":trixnity-serverserverapi:trixnity-serverserverapi-model"))

                api(libs.ktor.server.auth)
                implementation(libs.ktor.server.doubleReceive)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)

                implementation(libs.ktor.server.testHost)
                implementation(libs.ktor.server.resources)
                implementation(libs.ktor.server.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.kotest.assertions.core)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}