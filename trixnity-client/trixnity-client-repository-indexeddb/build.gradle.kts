plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.kotest.multiplatform")
}

kotlin {
    jvmToolchain()
    addJsTarget(rootDir, nodeJsEnabled = false)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val jsMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))

                implementation("io.github.oshai:kotlin-logging:${Versions.kotlinLogging}")

                api("com.juul.indexeddb:core:${Versions.juulLabsIndexeddb}")
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":trixnity-client:client-repository-test"))
                implementation("com.benasher44:uuid:${Versions.uuid}")
            }
        }
    }
}
