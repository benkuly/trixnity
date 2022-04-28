import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget.MINGW_X64

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val currentPlatform: String = com.sun.jna.Platform.RESOURCE_PREFIX
val windowsAmd64Platform = "win32-x86-64"
val jvmProcessedResourcesDir = buildDir.resolve("processedResources").resolve("jvm").resolve("main")
val olmRootDir = buildDir.resolve("olm").resolve(Versions.olm)
val olmTmpDir = buildDir.resolve("tmp")
val olmZipDir = olmTmpDir.resolve("olm-${Versions.olm}.zip")
val olmBuildDir = olmRootDir.resolve("build")
val olmBuildCurrentPlatformDir = olmBuildDir.resolve(currentPlatform)
val olmBuildWinAmd64Dir = olmBuildDir.resolve(windowsAmd64Platform)
val olmIncludeDir = olmRootDir.resolve("include")
val olmCMakeLists = olmRootDir.resolve("CMakeLists.txt")

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
            path = file(olmCMakeLists)
            version = Versions.cmake
        }
    }
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
    android {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions.jvmTarget = Versions.kotlinJvmTarget.toString()
        }
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
                implementation("com.soywiz.korlibs.krypto:krypto:${Versions.korlibs}")
            }
        }
        val olmLibraryMain by creating {
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
        val nativeMain by creating {
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
        val nativeTest by creating {
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
                            dependsOn(buildOlm)
                        }
                        includeDirs(olmIncludeDir)
                    }
                }
                binaries.all {
                    when (konanTarget) {
                        MINGW_X64 -> linkerOpts(
                            "-L${olmBuildWinAmd64Dir}", "-lolm"
                        )
                        else -> linkerOpts(
                            "-L${olmBuildCurrentPlatformDir}", "-lolm"
                        )
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
    environment("LD_LIBRARY_PATH", olmBuildCurrentPlatformDir)
    if (HostManager.hostIsMingw) {
        environment("Path", olmBuildWinAmd64Dir)
    }
}

tasks.withType<ExternalNativeCleanTask> {
    enabled = false
}

tasks.withType<ExternalNativeBuildTask> {
    dependsOn(extractOlm)
}

project.afterEvaluate {
    val testTasks =
        project.getTasksByName("testReleaseUnitTest", false) + project.getTasksByName("testDebugUnitTest", false)
    testTasks.forEach {
        it.onlyIf { false }
    }
}

val downloadOlm by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "olm"
    src("https://gitlab.matrix.org/matrix-org/olm/-/archive/${Versions.olm}/olm-${Versions.olm}.zip")
    dest(olmZipDir)
    overwrite(false)
}

val extractOlm by tasks.registering(Copy::class) {
    group = "olm"
    from(zipTree(olmZipDir)) {
        include("olm-${Versions.olm}/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into(olmRootDir)
    dependsOn(downloadOlm)
}

val prepareBuildOlmWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmRootDir)
    // TODO we disabled tests, because the linking of them fails
    commandLine(
        "cmake", ".", "-B${olmBuildWinAmd64Dir.absolutePath}",
        "-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake", "-DOLM_TESTS=OFF"
    )
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildWinAmd64Dir)
}

val buildOlmWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmBuildWinAmd64Dir)
    commandLine("cmake", "--build", ".")
    dependsOn(prepareBuildOlmWindows)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildWinAmd64Dir)
}

val prepareBuildOlmCurrentPlatform by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmRootDir)
    commandLine("cmake", ".", "-B${olmBuildCurrentPlatformDir.absolutePath}")
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildCurrentPlatformDir)
}

val buildOlmCurrentPlatform by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmBuildCurrentPlatformDir)
    commandLine("cmake", "--build", ".")
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(prepareBuildOlmCurrentPlatform)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildCurrentPlatformDir)
}

val buildOlm by tasks.registering {
    dependsOn(buildOlmCurrentPlatform, buildOlmWindows)
    group = "olm"
}

val installOlmToResourcesCurrentPlatform by tasks.registering(Copy::class) {
    group = "olm"
    from(olmBuildCurrentPlatformDir)
    include("libolm.so", "libolm.dll", "libolm.dylib")
    into(jvmProcessedResourcesDir.resolve(currentPlatform))
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(buildOlm)
}

val installOlmToResourcesWindows by tasks.registering(Copy::class) {
    group = "olm"
    from(olmBuildWinAmd64Dir)
    include("libolm.dll")
    into(jvmProcessedResourcesDir.resolve(windowsAmd64Platform))
    dependsOn(buildOlm)
}

tasks.withType<ProcessResources> {
    dependsOn(installOlmToResourcesCurrentPlatform, installOlmToResourcesWindows)
}