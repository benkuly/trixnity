import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget.LINUX_X64
import org.jetbrains.kotlin.konan.target.KonanTarget.MINGW_X64

plugins {
    id("com.android.library")
    kotlin("plugin.serialization")
    kotlin("multiplatform")
}

android {
    compileSdk = Versions.androidTargetSdk
    buildToolsVersion = Versions.androidBuildTools
    ndkVersion = Versions.androidNdk
    defaultConfig {
        minSdk = Versions.androidMinSdk
        targetSdk = Versions.androidTargetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main") {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    externalNativeBuild {
        cmake {
            path = file(olm.cMakeLists)
            version = Versions.cmake
        }
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
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                    useConfigDirectory(rootDir.resolve("karma.config.d"))
                }
            }
        }
        binaries.executable()
    }

    linuxX64()
    mingwX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("io.ktor:ktor-utils:${Versions.ktor}")
            }
        }
        val olmLibraryMain = create("olmLibraryMain") {
            dependsOn(commonMain)
        }
        val jvmMain by getting {
            dependsOn(olmLibraryMain)
            dependencies {
                implementation("net.java.dev.jna:jna:${Versions.jna}")
            }
        }
        val androidMain by getting {
            dependsOn(olmLibraryMain)
            kotlin.srcDirs("src/jvmMain/kotlin")
            dependencies {
                api("net.java.dev.jna:jna:${Versions.jna}@aar")
            }
        }
        val nativeMain = create("nativeMain") {
            dependsOn(olmLibraryMain)
        }
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("@matrix-org/olm", Versions.olm, generateExternals = false))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
            }
        }
        val jvmTest by getting {}
        val androidTest by getting {
            kotlin.srcDirs("src/jvmTest/kotlin")
            dependencies {
                implementation("androidx.test:runner:1.4.0")
            }
        }
        val jsTest by getting
        val nativeTest = create("nativeTest") {
            dependsOn(commonTest)
        }
        val linuxX64Test by getting {
            dependsOn(nativeTest)
        }
        val mingwX64Test by getting {
            dependsOn(nativeTest)
        }
    }
    targets.withType<KotlinNativeTarget> {
        compilations {
            "main" {
                cinterops {
                    create("libolm") {
                        tasks.named(interopProcessingTaskName) {
                            dependsOn(":buildOlm")
                        }
                        includeDirs(olm.include)
                    }
                }
                binaries.all {
                    when (konanTarget) {
                        LINUX_X64 -> linkerOpts(
                            "-L${olm.build}", "-lolm"
                        )
                        MINGW_X64 -> linkerOpts(
                            "-L${olm.buildWin}", "-lolm"
                        )
                        else -> {}
                    }
                }
                defaultSourceSet {
                    kotlin.srcDir("src/nativeMain/kotlin")
                    resources.srcDir("src/nativeMain/resources")
                }
                dependencies {
                }
            }
        }
    }
}

tasks.withType<Kotlin2JsCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xir-property-lazy-initialization"
    }
}

tasks.withType<KotlinNativeHostTest> {
    environment("LD_LIBRARY_PATH", olm.build)
    if (HostManager.hostIsMingw) {
        environment("Path", olm.buildWin)
    }
}

tasks.withType<ExternalNativeCleanTask> {
    enabled = false
}

tasks.withType<ExternalNativeBuildTask> {
    dependsOn(":extractOlm")
}

project.afterEvaluate {
    val testTasks =
        project.getTasksByName("testReleaseUnitTest", false) + project.getTasksByName("testDebugUnitTest", false)
    testTasks.forEach {
        it.onlyIf { false }
    }
}