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
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)
                implementation(projects.trixnityClient.trixnityClientRepositoryMigrationLibvodozemac)
                implementation(projects.trixnityVodozemac)

                api(projects.trixnityTestUtils)
            }
        }
    }
}