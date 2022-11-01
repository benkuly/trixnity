import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    if (isAndroidEnabled) id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("de.undercouch.download") version Versions.downloadGradlePlugin
}

val jvmProcessedResourcesDir = buildDir.resolve("processedResources").resolve("jvm").resolve("main")

val olmSourcesDir = buildDir.resolve("olm-sources").resolve(Versions.olm)
val olmBinariesDir = buildDir.resolve("olm-binaries").resolve(Versions.olmBinaries)
val olmTmpDir = buildDir.resolve("tmp")
val olmSourceZipDir = olmTmpDir.resolve("olm-${Versions.olm}.zip")
val olmBinariesZipDir = olmTmpDir.resolve("olm-${Versions.olmBinaries}-binaries.zip")

val olmIncludeDir = olmSourcesDir.resolve("include")
val olmSharedLibPath = olmBinariesDir.resolve("shared")
val olmSharedAndroidLibPath = olmBinariesDir.resolve("shared-android")
val olmStaticLibPath = olmBinariesDir.resolve("static")

data class OlmNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinTargetContainerWithNativeShortcuts.() -> KotlinNativeTarget,
) {
    val libDir = olmStaticLibPath.resolve(target.name)
    val libPath = libDir.resolve("libolm.a")
    val enabledOnThisPlatform = target.isEnabledOnThisPlatform()
}

val olmNativeTargets = listOf(
    OlmNativeTarget(
        target = KonanTarget.LINUX_X64,
        createTarget = { linuxX64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.MACOS_ARM64,
        createTarget = { macosArm64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.MACOS_X64,
        createTarget = { macosX64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.MINGW_X64,
        createTarget = { mingwX64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_SIMULATOR_ARM64,
        createTarget = { iosSimulatorArm64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_ARM64,
        createTarget = { iosArm64() },
    ),
    OlmNativeTarget(
        target = KonanTarget.IOS_X64,
        createTarget = { iosX64() },
    ),
)

if (isAndroidEnabled) {
    configure<LibraryExtension> {
        compileSdk = Versions.androidTargetSdk
        buildToolsVersion = Versions.androidBuildTools
        defaultConfig {
            minSdk = Versions.androidMinSdk
            targetSdk = Versions.androidTargetSdk
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        sourceSets.getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            jniLibs.srcDirs(olmSharedAndroidLibPath)
        }
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
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
                                    dependsOn(extractOlmSources, extractOlmBinaries)
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
                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
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

project.afterEvaluate {
    val testTasks =
        project.getTasksByName("testReleaseUnitTest", false) + project.getTasksByName("testDebugUnitTest", false)
    testTasks.forEach {
        it.onlyIf { false }
    }
}

val downloadOlmSources by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "olm"
    src("https://gitlab.matrix.org/matrix-org/olm/-/archive/${Versions.olm}/olm-${Versions.olm}.zip")
    dest(olmSourceZipDir)
    overwrite(false)
}

val extractOlmSources by tasks.registering(Copy::class) {
    group = "olm"
    from(zipTree(olmSourceZipDir)) {
        include("olm-${Versions.olm}/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into(olmSourcesDir)
    outputs.cacheIf { true }
    inputs.files(olmSourceZipDir)
    dependsOn(downloadOlmSources)
}

val downloadOlmBinaries by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    group = "olm"
    src("https://gitlab.com/api/v4/projects/39141647/packages/generic/binaries/${Versions.olmBinaries}/binaries.zip")
    dest(olmBinariesZipDir)
    overwrite(false)
}

val extractOlmBinaries by tasks.registering(Copy::class) {
    group = "olm"
    from(zipTree(olmBinariesZipDir)) {
        include("binaries/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into(olmBinariesDir)
    outputs.cacheIf { true }
    inputs.files(olmBinariesZipDir)
    dependsOn(downloadOlmBinaries)
}

val installOlmToJvmResources by tasks.registering(Copy::class) {
    group = "olm"
    from(olmSharedLibPath)
    include("*/libolm.so", "*/olm.dll", "*/libolm.dylib")
    into(jvmProcessedResourcesDir)
    dependsOn(extractOlmBinaries)
}

tasks.withType<ProcessResources> {
    dependsOn(installOlmToJvmResources)
}

tasks.withType<ExternalNativeCleanTask> {
    enabled = false
}

tasks.withType<ExternalNativeBuildTask> {
    dependsOn(extractOlmBinaries)
}