import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.io.File

fun KotlinMultiplatformExtension.addJvmTarget(
    useJUnitPlatform: Boolean = true,
    testEnabled: Boolean = true
): KotlinJvmTarget =
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = Versions.kotlinJvmTarget.toString()
        }
        testRuns["test"].executionTask.configure {
            enabled = testEnabled
            if (useJUnitPlatform) useJUnitPlatform()
        }
    }

fun KotlinMultiplatformExtension.addAndroidTarget() =
    android {
        publishLibraryVariants("release")
    }

fun KotlinMultiplatformExtension.addJsTarget(
    rootDir: File,
    testEnabled: Boolean = true,
    browserEnabled: Boolean = true,
    nodeJsEnabled: Boolean = true,
): KotlinJsTargetDsl =
    js(IR) {
        if (browserEnabled)
            browser {
                commonWebpackConfig {
                    configDirectory = rootDir.resolve("webpack.config.d")
                }
                testTask {
                    useKarma {
                        useFirefoxHeadless()
                        useConfigDirectory(rootDir.resolve("karma.config.d"))
                    }
                    enabled = testEnabled
                }
            }
        if (nodeJsEnabled)
            nodejs {
                testTask {
                    useMocha {
                        timeout = "30000"
                    }
                    enabled = testEnabled
                }
            }
        binaries.executable()
    }

fun KotlinMultiplatformExtension.addNativeTargets(): Set<KotlinNativeTarget> =
    addNativeDesktopTargets() + addNativeAppleTargets()

fun KotlinMultiplatformExtension.addNativeDesktopTargets(): Set<KotlinNativeTarget> =
    setOf(
        linuxX64(),
        mingwX64(),
    )

fun KotlinMultiplatformExtension.addNativeAppleTargets(): Set<KotlinNativeTarget> =
    setOf(
        macosX64(),
        macosArm64(),
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    )