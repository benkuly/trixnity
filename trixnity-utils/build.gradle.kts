plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
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
                api(sharedLibs.ktor.utils)
                api(sharedLibs.kotlinx.coroutines.core)
                api(libs.okio)
                api(libs.oshai.logging)
            }
        }
        jsMain {
            dependencies {
                api(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
                api(sharedLibs.kotlin.browser)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}