plugins {
    kotlin("multiplatform")
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
                api(libs.ktor.utils)
                api(libs.kotlinx.coroutines.core)
                api(libs.okio)
                implementation(libs.oshai.logging)
            }
        }
        jsMain {
            dependencies {
                api(project.dependencies.platform(libs.kotlin.wrappers.bom))
                api(libs.kotlin.wrappers.browser)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}