plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlinxKover)
    alias(libs.plugins.mokkery)
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
                api(projects.trixnityClientserverapi.trixnityClientserverapiModel)

                implementation(libs.ktor.server.contentNegotiation)
                api(libs.ktor.server.auth)
                implementation(libs.ktor.server.cors)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)

                implementation(libs.ktor.server.testHost)
                implementation(libs.ktor.server.contentNegotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.server.resources)

                implementation(libs.kotest.assertions.core)

                implementation(projects.trixnityTestUtils)
            }
        }
    }
}