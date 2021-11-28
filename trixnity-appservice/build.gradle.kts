plugins {
    kotlin("plugin.serialization")
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_11.ordinal))
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        withJava()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-client-api"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")

                implementation("io.ktor:ktor-server-core:${Versions.ktor}")

                implementation("io.ktor:ktor-auth:${Versions.ktor}")
                implementation("io.ktor:ktor-serialization:${Versions.ktor}")

                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                implementation("com.benasher44:uuid:${Versions.uuid}")

                implementation("org.kodein.log:kodein-log:${Versions.kodeinLog}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.mockk:mockk:${Versions.mockk}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.ktor:ktor-server-test-host:${Versions.ktor}")
            }
        }
    }
}