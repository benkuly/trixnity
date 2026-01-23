plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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

                implementation(libs.lognity.api)

                api(libs.juulLabs.indexeddb)
            }
        }
        jsTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.trixnityClient.clientRepositoryTest)
                implementation(projects.idbSchemaexporter)
                implementation(sharedLibs.kotest.assertions.json)
            }
        }
    }
}
