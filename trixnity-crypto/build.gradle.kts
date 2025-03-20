plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotest)
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
                api(projects.trixnityCore)
                api(projects.trixnityCryptoCore)
                api(projects.trixnityOlm)
                api(projects.trixnityClientserverapi.trixnityClientserverapiModel)

                implementation(libs.kotlinx.datetime)
                implementation(libs.oshai.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.common)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.logback.classic)
            }
        }
    }
}