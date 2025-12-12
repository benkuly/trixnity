@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.download)
    alias(libs.plugins.kotlinxKover)
    trixnity.publish
}

val olmBinariesDirs = TrixnityOlmBinariesDirs(project, libs.versions.trixnityOlmBinaries.get())

class OlmNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinMultiplatformExtension.() -> KotlinNativeTarget,
) {
    val libPath: File = olmBinariesDirs.binStatic.resolve(target.name).resolve("libolm.a")
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

val installOlmToJvmResources by tasks.registering(Copy::class) {
    group = "olm"
    from(olmBinariesDirs.binShared)
    include("*/libolm.so", "*/olm.dll", "*/libolm.dylib")
    into(layout.buildDirectory.dir("processedResources/jvm/main"))
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

android {
    namespace = "net.folivo.trixnity.libolm"
    compileSdk = libs.versions.androidTargetSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main") {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        jniLibs.srcDirs(olmBinariesDirs.binSharedAndroid)
    }
    buildTypes {
        release {
            isDefault = true
        }
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
        }
    }
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}
tasks.withType(com.android.build.gradle.tasks.MergeSourceSetFolders::class).configureEach {
    if (name.contains("jni", true)) {
        dependsOn(trixnityBinariesTask)
    }
}

val desktopOlmLibs by tasks.registering(Jar::class) {
    dependsOn(trixnityBinariesTask)

    from(olmBinariesDirs.binShared)
    archiveBaseName = "trixnity-olm-desktop-libs"
    destinationDirectory = layout.buildDirectory.dir("tmp")
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addAndroidTarget()
    addJsTarget(rootDir)

    applyDefaultHierarchyTemplate {
        common {
            group("olmLibrary") {
                group("jna") {
                    withJvm()
                    withAndroidTarget()
                }
                group("native") {
                    group("linux")
                    group("mingw")
                    group("apple")
                }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    olmNativeTargetList.forEach { target ->
        target.createTarget(this).apply {
            compilations {
                "main" {
                    cinterops {
                        val libolm by creating {
                            packageName("org.matrix.olm")
                            includeDirs(olmBinariesDirs.headers)
                            tasks.named(interopProcessingTaskName) {
                                dependsOn(trixnityBinariesTask)
                            }
                        }
                    }
                }
            }
            compilerOptions {
                freeCompilerArgs.addAll(listOf("-include-binary", target.libPath.absolutePath))
            }
        }
    }

    sourceSets {
        configureEach {
            languageSettings.optIn("kotlin.RequiresOptIn")
            if (isNativeOnly) languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonMain {
            dependencies {
                implementation(projects.trixnityCryptoCore)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.utils)
                implementation(libs.oshai.logging)
            }
        }
        // TODO: proper kotlin multiplatform variant of using jna
        val jnaMain by getting {
            dependencies {
                compileOnly(libs.jna)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.jna.get().toString() + "@aar")
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.jna.get().toString() + "@jar")
            }
        }
        jsMain {
            dependencies {
                implementation(
                    npm(
                        "trixnity-olm-wrapper",
                        "https://gitlab.com/api/v4/projects/46553592/packages/generic/build/v${libs.versions.trixnityOlmBinaries.get()}/trixnity-olm-wrapper.tgz"
                    )
                )
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
        androidUnitTest {
            dependencies {
                implementation(files(desktopOlmLibs))
                implementation(libs.jna.get().toString() + "@jar")
            }
        }
        androidInstrumentedTest {
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }
    }
}