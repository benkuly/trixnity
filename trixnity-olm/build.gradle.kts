import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val jvmProcessedResourcesDir = buildDir.resolve("processedResources").resolve("jvm").resolve("main")

val olmRootDir = buildDir.resolve("olm").resolve(Versions.olm)
val olmTmpDir = buildDir.resolve("tmp")
val olmZipDir = olmTmpDir.resolve("olm-${Versions.olm}.zip")
val olmIncludeDir = olmRootDir.resolve("include")
val olmCMakeLists = olmRootDir.resolve("CMakeLists.txt")
val olmJNALibPath = olmRootDir.resolve("build-jna")
val olmNativeLibPath = olmRootDir.resolve("build-native")

data class OlmJNATarget(
    val name: String,
    val onlyIf: () -> Boolean,
    val additionalParams: List<String> = listOf()
) {
    val libDir = olmJNALibPath.resolve(name)
}

data class OlmNativeTarget(
    val target: KonanTarget,
    val onlyIf: () -> Boolean,
    val additionalParams: List<String> = listOf()
) {
    val libDir = olmNativeLibPath.resolve(target.name)
    val libPath = libDir.resolve("libolm.a")
}

val olmJNATargets = listOf(
    OlmJNATarget(
        name = "linux-x86-64",
        onlyIf = { HostManager.hostIsLinux }
    ),
    OlmJNATarget(
        name = "darwin",
        onlyIf = { HostManager.hostIsMac },
        additionalParams = listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64;arm64")
    ),
    OlmJNATarget(
        name = "win32-x86-64",
        onlyIf = { HostManager.hostIsMingw || HostManager.hostIsLinux },
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake")
    )
)

val olmNativeTargets = listOf(
    OlmNativeTarget(
        target = KonanTarget.LINUX_X64,
        onlyIf = { HostManager.hostIsLinux }
    ),
    OlmNativeTarget(
        target = KonanTarget.MACOS_ARM64,
        onlyIf = { HostManager.hostIsMac },
        additionalParams = listOf("-DCMAKE_OSX_ARCHITECTURES=arm64")
    ),
    OlmNativeTarget(
        target = KonanTarget.MACOS_X64,
        onlyIf = { HostManager.hostIsMac },
        additionalParams = listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64")
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_ARM64,
        onlyIf = { HostManager.hostIsMac },
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake", "-DPLATFORM=OS64")
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_X64,
        onlyIf = { HostManager.hostIsMac },
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake", "-DPLATFORM=SIMULATOR64")
    ),
    OlmNativeTarget(
        target = KonanTarget.MINGW_X64,
        onlyIf = { HostManager.hostIsMingw || HostManager.hostIsLinux },
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake")
    ),
)

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
    linuxX64()
    mingwX64()
    macosX64()
    macosArm64()
    ios()

    olmNativeTargets.forEach {
        targets.getByName<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>(it.target.presetName) {
            compilations {
                "main" {
                    cinterops {
                        val libolm by creating {
                            packageName("org.matrix.olm")
                            includeDirs(olmIncludeDir)
                            tasks.named(interopProcessingTaskName) {
                                dependsOn(olmNativeTargetsTasks)
                            }
                        }
                    }
                    kotlinOptions.freeCompilerArgs = listOf("-include-binary", it.libPath.absolutePath)
                }
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
        val androidTest by getting {
            kotlin.srcDirs("src/jvmTest/kotlin")
            dependencies {
                implementation("androidx.test:runner:${Versions.androidxTestRunner}")
            }
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

val downloadIOSCmakeToolchain by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "olm"
    src("https://raw.githubusercontent.com/leetal/ios-cmake/master/ios.toolchain.cmake")
    dest(olmRootDir)
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

val olmJNATargetsTasks = olmJNATargets.flatMap {
    if (it.onlyIf()) {
        val prepareTask = tasks.register<Exec>("prepareBuildOlmJNA${it.name}") {
            group = "olm"
            workingDir(olmRootDir)
            commandLine(
                "cmake", ".", "-B${it.libDir.absolutePath}",
                "-DOLM_TESTS=OFF",
                *it.additionalParams.toTypedArray()
            )
            dependsOn(extractOlm)
            outputs.cacheIf { true }
            inputs.files(olmCMakeLists)
            outputs.dir(it.libDir)
        }
        val buildTask = tasks.register<Exec>("buildOlmJNA${it.name}") {
            group = "olm"
            workingDir(it.libDir)
            commandLine("cmake", "--build", ".")
            dependsOn(prepareTask)
            outputs.cacheIf { true }
            inputs.files(olmCMakeLists)
            outputs.dir(it.libDir)
            doLast {// FIXME
                it.libDir.resolve("libolm.dll").renameTo(it.libDir.resolve("olm.dll"))
            }
        }
        listOf(prepareTask, buildTask)
    } else listOf()
}

val olmNativeTargetsTasks = olmNativeTargets.flatMap {
    if (it.onlyIf()) {
        val prepareTask = tasks.register<Exec>("prepareBuildOlmNative${it.target.presetName}") {
            group = "olm"
            workingDir(olmRootDir)
            commandLine(
                "cmake", ".", "-B${it.libDir.absolutePath}",
                "-DOLM_TESTS=OFF",
                "-DBUILD_SHARED_LIBS=NO",
                *it.additionalParams.toTypedArray()
            )
            dependsOn(extractOlm, downloadIOSCmakeToolchain)
            outputs.cacheIf { true }
            inputs.files(olmCMakeLists)
            outputs.dir(it.libDir)
        }
        val buildTask = tasks.register<Exec>("buildOlmNative${it.target.presetName}") {
            group = "olm"
            workingDir(it.libDir)
            commandLine("cmake", "--build", ".")
            dependsOn(prepareTask)
            outputs.cacheIf { true }
            inputs.files(olmCMakeLists)
            outputs.dir(it.libDir)
        }
        listOf(prepareTask, buildTask)
    } else listOf()
}

val buildOlm by tasks.registering {
    dependsOn(
        olmJNATargetsTasks + olmNativeTargetsTasks
    )
    group = "olm"
}

val installOlmToResources by tasks.registering(Copy::class) {
    group = "olm"
    from(olmJNALibPath)
    include("*/libolm.so", "*/olm.dll", "*/libolm.dylib")
    into(jvmProcessedResourcesDir)
    dependsOn(buildOlm)
}

tasks.withType<ProcessResources> {
    dependsOn(installOlmToResources)
}