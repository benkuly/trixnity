import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("de.undercouch.download")
    if (isAndroidEnabled) id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

val jvmProcessedResourcesDir = buildDir.resolve("processedResources").resolve("jvm").resolve("main")

val trixnityBinariesDirs = TrixnityBinariesDirs(project)

class OlmNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinTargetContainerWithNativeShortcuts.() -> KotlinNativeTarget,
) {
    val libPath: File = trixnityBinariesDirs.olmBinStaticDir.resolve(target.name).resolve("libolm.a")
    val enabledOnThisPlatform: Boolean = target.isEnabledOnThisPlatform()
}

val olmNativeTargetList = listOf(
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

project.afterEvaluate {
    val testTasks =
        project.getTasksByName("testReleaseUnitTest", false) +
                project.getTasksByName("testDebugUnitTest", false)
    testTasks.forEach {
        it.onlyIf { false }
    }
}

val installOlmToJvmResources by tasks.registering(Copy::class) {
    group = "olm"
    from(trixnityBinariesDirs.olmBinSharedDir)
    include("*/libolm.so", "*/olm.dll", "*/libolm.dylib")
    into(jvmProcessedResourcesDir)
    dependsOn(trixnityBinariesTask)
}

tasks.withType<ProcessResources> {
    dependsOn(installOlmToJvmResources)
}

tasks.withType<ExternalNativeCleanTask> {
    enabled = false
}

tasks.withType<ExternalNativeBuildTask> {
    dependsOn(trixnityBinariesTask)
}

if (isAndroidEnabled) {
    configure<LibraryExtension> {
        namespace = "net.folivo.trixnity.olm"
        compileSdk = Versions.androidTargetSdk
        buildToolsVersion = Versions.androidBuildTools
        defaultConfig {
            minSdk = Versions.androidMinSdk
            targetSdk = Versions.androidTargetSdk
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        sourceSets.getByName("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            jniLibs.srcDirs(trixnityBinariesDirs.olmBinSharedAndroidDir)
        }
        compileOptions {
            sourceCompatibility = Versions.kotlinJvmTarget
            targetCompatibility = Versions.kotlinJvmTarget
        }
        buildTypes {
            release {
                isDefault = true
            }
        }
    }
    tasks.withType(com.android.build.gradle.tasks.MergeSourceSetFolders::class).configureEach {
        if (name.contains("jni", true)) {
            dependsOn(trixnityBinariesTask)
        }
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    targetHierarchy.default()
    jvmToolchain()
    val jvmTarget = addDefaultJvmTargetWhenEnabled()
    val androidJvmTarget = addTargetWhenEnabled(KotlinPlatformType.androidJvm) {
        android {
            publishLibraryVariants("release")
        }
    }
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir)

    val nativeTargets = olmNativeTargetList.mapNotNull { target ->
        addNativeTargetWhenEnabled(target.target) {
            target.createTarget(this).apply {
                compilations {
                    "main" {
                        cinterops {
                            val libolm by creating {
                                packageName("org.matrix.olm")
                                includeDirs(trixnityBinariesDirs.olmHeadersDir)
                                tasks.named(interopProcessingTaskName) {
                                    dependsOn(trixnityBinariesTask)
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
                implementation(project(":trixnity-crypto-core"))
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
        val nativeMain by getting {
            dependsOn(olmLibraryMain)
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