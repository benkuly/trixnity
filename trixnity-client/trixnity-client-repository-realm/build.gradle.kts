plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.realm.kotlin")
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addNativeAppleTargets() // see https://github.com/realm/realm-kotlin/issues/617

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")

                implementation("io.github.oshai:kotlin-logging:${Versions.kotlinLogging}")

                api("io.realm.kotlin:library-base:${Versions.realm}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":trixnity-client:client-repository-test"))
                implementation("com.benasher44:uuid:${Versions.uuid}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}