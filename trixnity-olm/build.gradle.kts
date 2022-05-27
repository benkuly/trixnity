import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

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

val olmBuildDirDynamic = olmRootDir.resolve("build-dynamic")
val olmBuildDirStatic = olmRootDir.resolve("build-static")
val olmBuildDynamicCurrentPlatformDir = olmBuildDirDynamic.resolve(currentPlatform)
val olmBuildDynamicWinAmd64Dir = olmBuildDirDynamic.resolve(windowsAmd64Platform)

val olmBuildStaticCurrentPlatformDir = olmBuildDirStatic.resolve("current")
val olmBuildStaticWinAmd64Dir = olmBuildDirStatic.resolve(KonanTarget.MINGW_X64.name)
val olmStaticBinaryCurrentPlatform = olmBuildStaticCurrentPlatformDir.resolve("libolm.a")
val olmStaticBinaryWinAmd64 = olmBuildStaticCurrentPlatformDir.resolve("libolm.a")

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
                    webpackConfig.configDirectory = rootDir.resolve("webpack.config.d")
                }
            }
        }
        nodejs()
        binaries.executable()
    }

    fun DefaultCInteropSettings.olmSettings() {
        packageName("org.matrix.olm")
        includeDirs(olmIncludeDir)
    }

    linuxX64 {
        compilations {
            "main" {
                cinterops {
                    val libolm by creating {
                        olmSettings()
                        tasks.named(interopProcessingTaskName) {
                            dependsOn(buildOlmStaticCurrentPlatform)
                        }
                    }
                }
                kotlinOptions.freeCompilerArgs = listOf("-include-binary", olmStaticBinaryCurrentPlatform.path)
            }
        }
    }
    mingwX64 {
        compilations {
            "main" {
                cinterops {
                    val libolm by creating {
                        olmSettings()
                        tasks.named(interopProcessingTaskName) {
                            dependsOn(buildOlmStaticWindows)
                        }
                    }
                }
                kotlinOptions.freeCompilerArgs = listOf("-include-binary", olmStaticBinaryCurrentPlatform.path)
            }
        }
    }

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
        val jvmTest by getting
        val androidTest by getting {
            kotlin.srcDirs("src/jvmTest/kotlin")
            dependencies {
                implementation("androidx.test:runner:${Versions.androidxTestRunner}")
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

val prepareBuildOlmDynamicWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmRootDir)
    commandLine(
        "cmake", ".", "-B${olmBuildDynamicWinAmd64Dir.absolutePath}",
        "-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake",
        "-DOLM_TESTS=OFF"
    )
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildDynamicWinAmd64Dir)
}

val buildOlmDynamicWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmBuildDynamicWinAmd64Dir)
    commandLine("cmake", "--build", ".")
    dependsOn(prepareBuildOlmDynamicWindows)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildDynamicWinAmd64Dir)
    doLast {
        olmBuildDynamicWinAmd64Dir.resolve("libolm.dll").renameTo(olmBuildDynamicWinAmd64Dir.resolve("olm.dll"))
    }
}

val prepareBuildOlmStaticWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmRootDir)
    commandLine(
        "cmake", ".", "-B${olmBuildStaticWinAmd64Dir.absolutePath}",
        "-DBUILD_SHARED_LIBS=NO",
        "-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake",
        "-DOLM_TESTS=OFF"
    )
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildStaticWinAmd64Dir)
}

val buildOlmStaticWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmBuildDynamicWinAmd64Dir)
    commandLine("cmake", "--build", ".")
    dependsOn(prepareBuildOlmStaticWindows)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildStaticWinAmd64Dir)
}

val prepareBuildOlmDynamicCurrentPlatform by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmRootDir)
    commandLine(
        "cmake", ".", "-B${olmBuildDynamicCurrentPlatformDir.absolutePath}",
        "-DOLM_TESTS=OFF"
    )
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildDynamicCurrentPlatformDir)
}

val buildOlmDynamicCurrentPlatform by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmBuildDynamicCurrentPlatformDir)
    commandLine("cmake", "--build", ".")
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(prepareBuildOlmDynamicCurrentPlatform)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildDynamicCurrentPlatformDir)
}

val prepareBuildOlmStaticCurrentPlatform by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmRootDir)
    commandLine(
        "cmake", ".", "-B${olmBuildStaticCurrentPlatformDir.absolutePath}",
        "-DBUILD_SHARED_LIBS=NO",
        "-DOLM_TESTS=OFF"
    )
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(olmBuildStaticCurrentPlatformDir)
}

val buildOlmStaticCurrentPlatform by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olmBuildStaticCurrentPlatformDir)
    commandLine("cmake", "--build", ".")
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(prepareBuildOlmStaticCurrentPlatform)
    outputs.cacheIf { true }
    inputs.files(olmCMakeLists)
    outputs.dir(prepareBuildOlmStaticCurrentPlatform)
}

val buildOlm by tasks.registering {
    dependsOn(
        buildOlmDynamicCurrentPlatform,
        buildOlmStaticCurrentPlatform,
        buildOlmDynamicWindows,
        buildOlmStaticWindows
    )
    group = "olm"
}

val installOlmToResourcesCurrentPlatform by tasks.registering(Copy::class) {
    group = "olm"
    from(olmBuildDynamicCurrentPlatformDir)
    include("libolm.so", "olm.dll", "libolm.dylib")
    into(jvmProcessedResourcesDir.resolve(currentPlatform))
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(buildOlm)
}

val installOlmToResourcesWindows by tasks.registering(Copy::class) {
    group = "olm"
    from(olmBuildDynamicWinAmd64Dir)
    include("olm.dll")
    into(jvmProcessedResourcesDir.resolve(windowsAmd64Platform))
    dependsOn(buildOlm)
}

tasks.withType<ProcessResources> {
    dependsOn(installOlmToResourcesCurrentPlatform, installOlmToResourcesWindows)
}