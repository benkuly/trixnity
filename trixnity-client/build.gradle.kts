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
                useKarma {
                    useFirefoxHeadless()
                    useConfigDirectory(rootDir.resolve("karma.config.d"))
                    webpackConfig.configDirectory = rootDir.resolve("webpack.config.d")
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
                api(project(":trixnity-clientserverapi:trixnity-clientserverapi-client"))
                implementation(project(":trixnity-olm"))

                implementation("io.ktor:ktor-client-core:${Versions.ktor}")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")

                implementation("io.arrow-kt:arrow-fx-coroutines:${Versions.arrow}")

                implementation("com.benasher44:uuid:${Versions.uuid}")

                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")

                implementation("com.soywiz.korlibs.krypto:krypto:${Versions.korlibs}")
                implementation("com.soywiz.korlibs.korim:korim:${Versions.korlibs}")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("net.coobird:thumbnailator:${Versions.thumbnailator}")
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("crypto-js", Versions.cryptoJs))
//                implementation(npm("@types/crypto-js", Versions.cryptoJs, true))
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
