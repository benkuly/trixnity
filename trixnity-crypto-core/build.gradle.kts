import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotest)
}

val trixnityBinariesDirs = TrixnityBinariesDirs(project, libs.versions.trixnityBinaries.get())

class OpensslNativeTarget(
    val target: KonanTarget,
    val createTarget: KotlinTargetContainerWithNativeShortcuts.() -> KotlinNativeTarget,
) {
    val libPath: File = trixnityBinariesDirs.opensslBinStaticDir.resolve(target.name).resolve("libcrypto.a")
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
                            includeDirs(trixnityBinariesDirs.opensslHeadersDir)
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
                api(project(":trixnity-utils"))

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
                implementation(libs.kotest.common)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)

                implementation(libs.korlibs.krypto)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.logback.classic)
            }
        }
    }
}