plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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

                implementation(sharedLibs.lognity.api)
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