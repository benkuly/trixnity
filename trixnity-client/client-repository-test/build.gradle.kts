plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotest)
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
                implementation(project(":trixnity-client"))

                implementation(libs.oshai.logging)

                api(libs.kotest.common)
                api(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}