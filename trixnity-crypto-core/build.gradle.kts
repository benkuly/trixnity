import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlinxKover)
    trixnity.publish
}

val opensslBinariesDirs = TrixnityOpensslBinariesDirs(project, libs.versions.trixnityOpensslBinaries.get())

class OpensslNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinMultiplatformExtension.() -> KotlinNativeTarget,
) {
    val libPath: File = opensslBinariesDirs.lib(target).resolve("libcrypto.a")
    val includePath: File = opensslBinariesDirs.include(target)
}

val opensslNativeTargetList = listOf(
    OpensslNativeTarget(
        target = KonanTarget.LINUX_X64,
        createTarget = { linuxX64() },
    ),
    OpensslNativeTarget(
        target = KonanTarget.MINGW_X64,
        createTarget = { mingwX64() },
    ),
)

kotlin {
    jvmToolchain()
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeAppleTargets()

    opensslNativeTargetList.onEach { target ->
        target.createTarget(this).apply {
            compilations {
                "main" {
                    cinterops {
                        val libopenssl by creating {
                            defFile("src/opensslMain/cinterop/libopenssl.def")
                            packageName("org.openssl")
                            includeDirs(target.includePath)
                            tasks.named(interopProcessingTaskName) {
                                dependsOn(trixnityBinariesTask)
                            }
                        }
                        if (target.target.family == Family.LINUX) {
                            val librandom by creating {
                                defFile("src/linuxMain/cinterop/librandom.def")
                                packageName("org.linux.random")
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
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        commonMain {
            dependencies {
                api(projects.trixnityUtils)

                implementation(libs.oshai.logging)
            }
        }

        val opensslMain by creating {
            dependsOn(commonMain.get())
        }
        val linuxMain by creating {
            dependsOn(opensslMain)
        }
        val linuxX64Main by getting {
            dependsOn(linuxMain)
        }
        val mingwMain by creating {
            dependsOn(opensslMain)
        }
        val mingwX64Main by getting {
            dependsOn(mingwMain)
        }

        val appleMain by creating {
            dependsOn(commonMain.get())
        }
        val macosX64Main by getting {
            dependsOn(appleMain)
        }
        val macosArm64Main by getting {
            dependsOn(appleMain)
        }
        val iosArm64Main by getting {
            dependsOn(appleMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(appleMain)
        }
        val iosX64Main by getting {
            dependsOn(appleMain)
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions.core)

                implementation(projects.trixnityTestUtils)
            }
        }
    }
}