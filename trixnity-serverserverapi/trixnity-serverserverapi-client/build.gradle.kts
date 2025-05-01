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
    addJsTarget(rootDir)
    addNativeTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                api(projects.trixnityApiClient)
                api(projects.trixnityServerserverapi.trixnityServerserverapiModel)

                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.client.resources)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.ktorTestUtils)

                implementation(libs.ktor.client.mock)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)

                implementation(projects.trixnityTestUtils)
            }
        }
    }
}