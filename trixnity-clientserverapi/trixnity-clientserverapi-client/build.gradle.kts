plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlinxKover)
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
                api(projects.trixnityCryptoCore)
                api(projects.trixnityApiClient)
                api(projects.trixnityClientserverapi.trixnityClientserverapiModel)

                api(libs.ktor.client.auth)
                implementation(libs.ktor.client.contentNegotiation)
                implementation(libs.ktor.client.resources)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.ktorTestUtils)

                implementation(libs.ktor.client.mock)
            }
        }
    }
}
