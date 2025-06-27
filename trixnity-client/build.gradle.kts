plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                api(projects.trixnityClientserverapi.trixnityClientserverapiClient)
                api(projects.trixnityCrypto)

                api(libs.koin.core)
                api(libs.kotlinx.datetime)

                implementation(libs.arrow.resilience)
                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.ktorTestUtils)

                implementation(libs.ktor.client.mock)

                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}
