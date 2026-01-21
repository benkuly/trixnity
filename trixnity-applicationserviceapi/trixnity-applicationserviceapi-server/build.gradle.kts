plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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
                api(projects.trixnityApplicationserviceapi.trixnityApplicationserviceapiModel)

                implementation(sharedLibs.ktor.server.core)
                implementation(sharedLibs.ktor.server.contentNegotiation)
                implementation(sharedLibs.ktor.server.resources)
                implementation(sharedLibs.ktor.server.auth)
                implementation(sharedLibs.ktor.serialization.kotlinx.json)

                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(sharedLibs.ktor.server.testHost)
            }
        }
    }
}