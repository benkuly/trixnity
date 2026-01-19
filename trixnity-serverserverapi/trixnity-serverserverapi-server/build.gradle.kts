plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(sharedLibs.plugins.mokkery)
    alias(sharedLibs.plugins.kotlinx.kover)
    `maven-publish`
    signing
}

kotlin {
    addJvmTarget()
    linuxX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                api(projects.trixnityApiServer)
                api(projects.trixnityServerserverapi.trixnityServerserverapiModel)

                implementation(sharedLibs.ktor.server.contentNegotiation)
                api(sharedLibs.ktor.server.auth)
                implementation(sharedLibs.ktor.server.doubleReceive)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)

                implementation(sharedLibs.ktor.server.testHost)
                implementation(sharedLibs.ktor.server.resources)
                implementation(sharedLibs.ktor.server.contentNegotiation)
                implementation(sharedLibs.ktor.serialization.kotlinx.json)

            }
        }
    }
}