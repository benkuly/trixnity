plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.kotest.multiplatform")
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
            useJUnitPlatform()
        }
    }
    js(IR) {
        browser {
            testTask {
                enabled = false
                useKarma {
                    useFirefoxHeadless()
                    useConfigDirectory(rootDir.resolve("karma.config.d"))
                    webpackConfig.configDirectory = rootDir.resolve("webpack.config.d")
                }
            }
        }
        nodejs {
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    linuxX64()
    mingwX64()
    macosX64()
    macosArm64()
    ios()

    targets.disableCompilationsOnCI()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-clientserverapi:trixnity-clientserverapi-client"))
                api(project(":trixnity-crypto"))

                implementation("io.ktor:ktor-client-core:${Versions.ktor}")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}") // FIXME still needed?

                implementation("io.arrow-kt:arrow-fx-coroutines:${Versions.arrow}")

                implementation("com.benasher44:uuid:${Versions.uuid}")

                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")

                implementation("com.soywiz.korlibs.korim:korim:${Versions.korlibs}")
                implementation("com.soywiz.korlibs.krypto:krypto:${Versions.korlibs}") // FIXME still needed?
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":test-utils"))

                implementation("io.ktor:ktor-client-mock:${Versions.ktor}")

                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-datatest:${Versions.kotest}")
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
