plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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

                implementation(sharedLibs.lognity.api)

                api(sharedLibs.ktor.client.mock)
                implementation(sharedLibs.ktor.resources)

                implementation(sharedLibs.kotest.assertions.core)
            }
        }
        commonTest {
            dependencies {
                implementation(sharedLibs.kotlin.test)
            }
        }
    }
}