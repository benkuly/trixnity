plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain()
    addJsTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        jsMain {
            dependencies {
                implementation(project(":trixnity-client"))

                api(project.dependencies.platform(libs.kotlin.wrappers.bom))
                api(libs.kotlin.wrappers.browser)
                implementation(libs.oshai.logging)
            }
        }
        jsTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}
