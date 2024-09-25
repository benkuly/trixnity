plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotest)
    trixnity.general
    trixnity.publish
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
                api(projects.trixnityClientserverapi.trixnityClientserverapiClient)
                api(projects.trixnityCrypto)

                api(libs.koin.core)

                api(libs.kotlinx.datetime)

                implementation(libs.arrow.resilience)

                implementation(libs.oshai.logging)

                implementation(libs.korlibs.korim)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.testUtils)

                implementation(libs.ktor.client.mock)

                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.common)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.framework.datatest)
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("reflect"))
                implementation(libs.kotest.runner.junit5)
                implementation(libs.logback.classic)
            }
        }
    }
}
