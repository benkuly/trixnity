import com.android.build.gradle.tasks.ExternalNativeBuildTask
import com.android.build.gradle.tasks.ExternalNativeCleanTask
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("com.android.library")
    alias(libs.plugins.download)
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    trixnity.general
    trixnity.publish
}

val olmBinariesDirs = TrixnityOlmBinariesDirs(project, libs.versions.trixnityOlmBinaries.get())

class OlmNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinTargetContainerWithNativeShortcuts.() -> KotlinNativeTarget,
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
    namespace = "net.folivo.trixnity.olm"
    compileSdk = libs.versions.androidTargetSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main") {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        jniLibs.srcDirs(olmBinariesDirs.binSharedAndroid)
    }
    compileOptions {
        sourceCompatibility = kotlinJvmTarget
        targetCompatibility = kotlinJvmTarget
    }
    buildTypes {
        release {
            isDefault = true
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

kotlin {
    jvmToolchain()
    addJvmTarget()
    addAndroidTarget()
    addJsTarget(rootDir)

    val nativeOlmTargets = olmNativeTargetList.map { target ->
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
                    kotlinOptions.freeCompilerArgs = listOf("-include-binary", target.libPath.absolutePath)
                }
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityCryptoCore)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.utils)
                implementation(libs.oshai.logging)
            }
        }
        val olmLibraryMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain {
            dependsOn(olmLibraryMain)
            dependencies {
                implementation(libs.jna)
            }
        }
        val androidMain by getting {
            dependsOn(olmLibraryMain)
            kotlin.srcDirs("src/jvmMain/kotlin")
            dependencies {
                api(libs.jna.get().toString() + "@aar")
            }
        }
        jsMain {
            dependencies {
                implementation(npm("@matrix-org/olm", libs.versions.olm.get()))
            }
        }
        val nativeMain by creating {
            dependsOn(olmLibraryMain)
        }
        nativeOlmTargets.forEach {
            getByName(it.targetName + "Main").dependsOn(nativeMain)
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)
            }
        }
        val androidUnitTest by getting { // TODO does not work
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }
    }
}