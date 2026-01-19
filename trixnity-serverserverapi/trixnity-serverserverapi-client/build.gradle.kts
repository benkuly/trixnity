plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(sharedLibs.plugins.kotlinx.kover)
    `maven-publish`
    signing
}

kotlin {
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

                implementation(sharedLibs.ktor.client.contentNegotiation)
                implementation(sharedLibs.ktor.client.resources)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.ktorTestUtils)

                implementation(sharedLibs.ktor.client.mock)
            }
        }
    }
}