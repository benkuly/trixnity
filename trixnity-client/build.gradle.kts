import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget.LINUX_X64
import org.jetbrains.kotlin.konan.target.KonanTarget.MINGW_X64

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
}

android {
    compileSdk = Versions.androidTargetSdk
    buildToolsVersion = Versions.androidBuildTools
    defaultConfig {
        minSdk = Versions.androidMinSdk
        targetSdk = Versions.androidTargetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main") {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            when (HostManager.host) {
                is LINUX_X64 -> {
                    systemProperty("java.library.path", olm.build.canonicalPath)
                    systemProperty("jna.library.path", olm.build.canonicalPath)
                }
                is MINGW_X64 -> {
                    systemProperty("java.library.path", olm.buildWin.canonicalPath)
                    systemProperty("jna.library.path", olm.buildWin.canonicalPath)
                }
                else -> {}
            }
        }
    }
    android {
        publishLibraryVariants("release")
    }
//    js(IR) {
//        browser {
//            testTask {
//                useKarma {
//                    useFirefoxHeadless()
//                    useConfigDirectory(rootDir.resolve("karma.config.d"))
//                }
//            }
//        }
//        binaries.executable()
//    }

//    linuxX64()
//    mingwX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-client-api"))
                implementation(project(":trixnity-olm"))
                implementation("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.kotlinxAtomicfu}")
                implementation("io.arrow-kt:arrow-fx-coroutines:${Versions.arrow}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        val androidAndJvmMain = create("androidAndJvmMain") {
            dependsOn(commonMain)
        }
        val jvmMain by getting {
            dependsOn(androidAndJvmMain)
            dependencies {
                implementation("net.coobird:thumbnailator:${Versions.thumbnailator}")
            }
        }
        val androidMain by getting {
            dependsOn(androidAndJvmMain)
        }
//        val jsMain by getting
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
                implementation("io.mockk:mockk:${Versions.mockk}")
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
//        val jsTest by getting
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
