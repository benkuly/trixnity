plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                api(projects.trixnityClientserverapi.trixnityClientserverapiModel)

                implementation(libs.oshai.logging)

                api(libs.ktor.client.mock)
                implementation(libs.ktor.resources)

                implementation(libs.kotest.assertions.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}