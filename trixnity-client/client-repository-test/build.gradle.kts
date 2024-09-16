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
    addJsTarget(rootDir)
    addNativeTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                implementation(libs.oshai.logging)

                api(libs.kotest.common)
                api(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}