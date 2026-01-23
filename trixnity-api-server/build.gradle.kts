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
                api(projects.trixnityCore)

                implementation(sharedLibs.ktor.server.core)
                implementation(sharedLibs.ktor.server.contentNegotiation)
                implementation(sharedLibs.ktor.server.resources)
                implementation(sharedLibs.ktor.server.statusPages)
                implementation(sharedLibs.ktor.serialization.kotlinx.json)

                implementation(libs.lognity.api)
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