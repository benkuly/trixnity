import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName

plugins {
    if (isAndroidEnabled) id("com.android.library")
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
    val compilationAllowed: Boolean,
    val additionalParams: List<String> = listOf()
) {
    val libDir = olmJNALibPath.resolve(name)
}

data class OlmNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinTargetContainerWithNativeShortcuts.() -> KotlinNativeTarget,
    val additionalParams: List<String> = listOf()
) {
    val libDir = olmNativeLibPath.resolve(target.name)
    val libPath = libDir.resolve("libolm.a")
    val enabledOnThisPlatform = target.isEnabledOnThisPlatform()
}

val olmJNATargets = listOf(
    OlmJNATarget(
        name = "linux-x86-64",
        compilationAllowed = KonanTarget.LINUX_X64.isEnabledOnThisPlatform()
    ),
    OlmJNATarget(
        name = "darwin",
        compilationAllowed = KonanTarget.MACOS_X64.isEnabledOnThisPlatform() && KonanTarget.MACOS_ARM64.isEnabledOnThisPlatform(),
        additionalParams = listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64;arm64")
    ),
    OlmJNATarget(
        name = "win32-x86-64",
        compilationAllowed = KonanTarget.MINGW_X64.isEnabledOnThisPlatform(),
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake")
    )
)

val olmNativeTargets = listOf(
    OlmNativeTarget(
        target = KonanTarget.LINUX_X64,
        createTarget = { linuxX64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.MACOS_ARM64,
        createTarget = { macosArm64() },
        additionalParams = listOf("-DCMAKE_OSX_ARCHITECTURES=arm64")
    ),
    OlmNativeTarget(
        target = KonanTarget.MACOS_X64,
        createTarget = { macosX64() },
        additionalParams = listOf("-DCMAKE_OSX_ARCHITECTURES=x86_64")
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_ARM64,
        createTarget = { iosArm64() },
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake", "-DPLATFORM=OS64")
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_X64,
        createTarget = { iosX64() },
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=ios.toolchain.cmake", "-DPLATFORM=SIMULATOR64")
    ),
    OlmNativeTarget(
        target = KonanTarget.MINGW_X64,
        createTarget = { mingwX64() },
        additionalParams = listOf("-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake")
    ),
)

if (isAndroidEnabled) {
    configure<LibraryExtension> {
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
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
    }
    val jvmTarget = addDefaultJvmTargetWhenEnabled(withJava = false)
    val androidJvmTarget = addTargetWhenEnabled(KotlinPlatformType.androidJvm) {
        android {
            publishLibraryVariants("release")
            compilations.all {
                kotlinOptions.jvmTarget = Versions.kotlinJvmTarget.toString()
            }
        }
    }
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir)

    val nativeTargets = olmNativeTargets.mapNotNull { target ->
        addNativeTargetWhenEnabled(target.target) {
            target.createTarget(this).apply {
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
                        kotlinOptions.freeCompilerArgs = listOf("-include-binary", target.libPath.absolutePath)
                    }
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
        jvmTarget?.mainSourceSet(this) {
            dependsOn(olmLibraryMain)
            dependencies {
                implementation("net.java.dev.jna:jna:${Versions.jna}")
            }
        }
        androidJvmTarget?.mainSourceSet(this) {
            dependsOn(olmLibraryMain)
            kotlin.srcDirs("src/jvmMain/kotlin")
            dependencies {
                api("net.java.dev.jna:jna:${Versions.jna}@aar")
            }
        }
        jsTarget?.mainSourceSet(this) {
            dependencies {
                implementation(npm("@matrix-org/olm", Versions.olm, generateExternals = false))
            }
        }
        val nativeMain by creating {
            dependsOn(olmLibraryMain)
        }
        nativeTargets.forEach {
            it.mainSourceSet(this).dependsOn(nativeMain)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
            }
        }
        androidJvmTarget?.testSourceSet(this) {
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
    dest(olmRootDir.resolve("ios.toolchain.cmake"))
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
    outputs.cacheIf { true }
    inputs.files(olmZipDir)
    dependsOn(downloadOlm)
}

val olmJNATargetsTasks = olmJNATargets.flatMap { target ->
    val prepareTask = tasks.register<Exec>("prepareBuildOlmJNA${target.name}") {
        group = "olm"
        workingDir(olmRootDir)
        commandLine(
            "cmake", ".", "-B${target.libDir.absolutePath}",
            "-DOLM_TESTS=OFF",
            *target.additionalParams.toTypedArray()
        )
        dependsOn(extractOlm)
        onlyIf { target.compilationAllowed }
        outputs.cacheIf { true }
        inputs.files(olmCMakeLists)
        outputs.dir(target.libDir)
    }
    val buildTask = tasks.register<Exec>("buildOlmJNA${target.name}") {
        group = "olm"
        workingDir(target.libDir)
        commandLine("cmake", "--build", ".")
        dependsOn(prepareTask)
        onlyIf { target.compilationAllowed }
        outputs.cacheIf { true }
        inputs.files(olmCMakeLists)
        outputs.dir(target.libDir)
        doLast {
            target.libDir.resolve("libolm.dll").renameTo(target.libDir.resolve("olm.dll"))
        }
    }
    listOf(prepareTask, buildTask)
}

val olmNativeTargetsTasks = olmNativeTargets.flatMap { target ->
    val prepareTask = tasks.register<Exec>("prepareBuildOlmNative${target.target.presetName}") {
        group = "olm"
        workingDir(olmRootDir)
        commandLine(
            "cmake", ".", "-B${target.libDir.absolutePath}",
            "-DOLM_TESTS=OFF",
            "-DBUILD_SHARED_LIBS=NO",
            *target.additionalParams.toTypedArray()
        )
        dependsOn(extractOlm, downloadIOSCmakeToolchain)
        onlyIf { target.enabledOnThisPlatform }
        outputs.cacheIf { true }
        inputs.files(olmCMakeLists)
        outputs.dir(target.libDir)
    }
    val buildTask = tasks.register<Exec>("buildOlmNative${target.target.presetName}") {
        group = "olm"
        workingDir(target.libDir)
        commandLine("cmake", "--build", ".")
        dependsOn(prepareTask)
        onlyIf { target.enabledOnThisPlatform }
        outputs.cacheIf { true }
        inputs.files(olmCMakeLists)
        outputs.dir(target.libDir)
    }
    listOf(prepareTask, buildTask)
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