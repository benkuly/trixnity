plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        withJava()
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                    useConfigDirectory(rootDir.resolve("karma.config.d"))
                }
            }
        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "30000"
                }
            }
        }
        binaries.executable()
    }

//    linuxX64()
//    mingwX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-core"))
                api(project(":trixnity-client-api-model"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                api("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("io.ktor:ktor-client-serialization:${Versions.ktor}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
//        val nativeMain = create("nativeMain") {
//            dependsOn(commonMain)
//        }
//        val linuxX64Main by getting {
//            dependsOn(nativeMain)
//        }
//        val mingwX64Main by getting {
//            dependsOn(nativeMain)
//        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:${Versions.ktor}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
        val jsTest by getting
//        val nativeTest = create("nativeTest") {
//            dependsOn(commonTest)
//        }
//        val linuxX64Test by getting {
//            dependsOn(nativeTest)
//        }
//        val mingwX64Test by getting {
//            dependsOn(nativeTest)
//        }
    }
}