plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.kodein.mock.mockmp")
}

mockmp {
    usesHelper = true
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = Versions.kotlinJvmTarget.toString()
        }
        testRuns["test"].executionTask.configure {
//            useJUnitPlatform()
        }
        withJava()
    }

    linuxX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-api-server"))
                api(project(":trixnity-clientserverapi:trixnity-clientserverapi-model"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

                implementation("io.ktor:ktor-server-auth:${Versions.ktor}")
                implementation("io.ktor:ktor-server-cors:${Versions.ktor}")

                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")

                implementation("io.ktor:ktor-server-test-host:${Versions.ktor}")
                implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktor}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}")
                implementation("io.ktor:ktor-server-resources:${Versions.ktor}")

                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}