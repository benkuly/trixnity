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
                implementation(projects.trixnityClient)

                implementation(libs.oshai.logging)

                api(kotlin("test"))
                api(libs.kotlinx.coroutines.test)
                api(libs.kotest.assertions.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }
}