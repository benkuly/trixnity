plugins {
    kotlin("plugin.serialization")
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("com.squareup.sqldelight:coroutines-extensions:${Versions.sqlDelight}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.mockk:mockk:${Versions.mockk}")
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
            }
        }
        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.exposed:exposed-core:${Versions.exposed}")
                implementation("org.jetbrains.exposed:exposed-dao:${Versions.exposed}")
                implementation("org.jetbrains.exposed:exposed-jdbc:${Versions.exposed}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("com.h2database:h2:${Versions.h2}")
            }
        }
    }
}
