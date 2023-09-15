import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.kotest.multiplatform")
}

val trixnityBinariesDirs = TrixnityBinariesDirs(project)

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
//    applyDefaultHierarchyTemplate{
//        group("native") {
//            group("openssl") {
//                withLinux()
//                withMingw()
//            }
//        }
//    }
    jvmToolchain()
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeAppleTargets()

    val opensslNativeTargets = opensslNativeTargetList.mapNotNull { target ->
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
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-utils"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("io.github.oshai:kotlin-logging:${Versions.kotlinLogging}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")

                implementation("com.soywiz.korlibs.krypto:krypto:${Versions.korlibs}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}