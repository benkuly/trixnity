plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
    signing
}

kotlin {
    addJsTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        jsMain {
            dependencies {
                implementation(projects.trixnityClient)

                api(project.dependencies.platform(libs.kotlin.wrappers.bom))
                api(libs.kotlin.wrappers.browser)
                implementation(libs.oshai.logging)
            }
        }
        jsTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}
