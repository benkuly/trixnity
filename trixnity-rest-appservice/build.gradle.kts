plugins {
    kotlin("plugin.serialization") version Versions.kotlin
    kotlin("multiplatform") version Versions.kotlin
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api(project(":trixnity-rest-client"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}-native-mt") {
                    version { strictly("${Versions.kotlinxCoroutines}-native-mt") }
                }

                implementation("io.ktor:ktor-server-core:${Versions.ktor}")

                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
                implementation("com.soywiz.korlibs.klogger:klogger:${Versions.klogger}")
            }
        }
        val commonTest by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
    }
}