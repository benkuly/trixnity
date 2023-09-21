plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotest)
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addJsTarget(rootDir, testEnabled = false)
    addNativeTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                api(project(":trixnity-clientserverapi:trixnity-clientserverapi-client"))
                api(project(":trixnity-crypto"))

                api(libs.koin.core)

                api(libs.kotlinx.datetime)

                implementation(libs.arrow.resilience)

                implementation(libs.benasher44.uuid)

                implementation(libs.oshai.logging)

                implementation(libs.korlibs.korim)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":test-utils"))

                implementation(libs.ktor.client.mock)

                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.common)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.framework.datatest)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.logback.classic)
            }
        }
    }
}
