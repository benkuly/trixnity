@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
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

    applyDefaultHierarchyTemplate {
        common {
            group("openssl") {
                group("linux")
                group("mingw")
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
                api(projects.trixnityUtils)

                implementation(libs.oshai.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
            }
        }
    }
}