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
            kotlinOptions.jvmTarget = kotlinJvmTarget.toString()
        }
        testRuns["test"].executionTask.configure {
            enabled = testEnabled
            if (useJUnitPlatform) useJUnitPlatform()
        }
    }

fun KotlinMultiplatformExtension.addAndroidTarget() =
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions.jvmTarget = kotlinJvmTarget.toString()
        }
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

fun KotlinMultiplatformExtension.addNativeTargets(configure: (KotlinNativeTarget.() -> Unit) = {}): Set<KotlinNativeTarget> =
    addNativeDesktopTargets(configure) + addNativeAppleTargets(configure)

fun KotlinMultiplatformExtension.addNativeDesktopTargets(configure: (KotlinNativeTarget.() -> Unit) = {}): Set<KotlinNativeTarget> =
    setOf(
        linuxX64(configure),
        mingwX64(configure),
    )

fun KotlinMultiplatformExtension.addNativeAppleTargets(configure: (KotlinNativeTarget.() -> Unit) = {}): Set<KotlinNativeTarget> =
    setOf(
        macosX64(configure),
        macosArm64(configure),
        iosArm64(configure),
        iosSimulatorArm64(configure),
        iosX64(configure),
    )