plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
            }
        }
    }
}
