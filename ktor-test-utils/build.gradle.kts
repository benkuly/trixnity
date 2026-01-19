plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
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

                api(sharedLibs.ktor.client.mock)
                implementation(sharedLibs.ktor.resources)

                implementation(sharedLibs.kotest.assertions.core)
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