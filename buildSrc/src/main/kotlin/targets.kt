import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import java.io.File

fun KotlinMultiplatformExtension.addJvmTarget(
    useJUnitPlatform: Boolean = true,
    testEnabled: Boolean = true,
    testConfig: KotlinJvmTest.() -> Unit = {},
): KotlinJvmTarget =
    jvm {
        testRuns["test"].executionTask.configure {
            enabled = testEnabled
            if (useJUnitPlatform) useJUnitPlatform()
            testConfig()
        }
    }

fun KotlinMultiplatformExtension.addAndroidTarget() =
    androidTarget { // TODO use androidLibrary
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
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
        useEsModules()
        binaries.executable()
    }

fun KotlinMultiplatformExtension.addNativeTargets(configure: (KotlinNativeTarget.() -> Unit) = {}): Set<KotlinNativeTarget> =
    addNativeDesktopTargets(configure) + addNativeAppleTargets(configure)

fun KotlinMultiplatformExtension.addNativeDesktopTargets(configure: (KotlinNativeTarget.() -> Unit) = {}): Set<KotlinNativeTarget> =
    setOf(
        linuxX64(configure),
        mingwX64(configure),
    )

fun KotlinMultiplatformExtension.addNativeAppleTargets(configure: (KotlinNativeTarget.() -> Unit) = {}): Set<KotlinNativeTarget> {
    val fullConfigure: (KotlinNativeTarget.() -> Unit) = {
        compilerOptions.freeCompilerArgs.add("-Xbinary=coreSymbolicationImageListType=ALL_LOADED") // TODO workaround for bug introduced in Kotlin 2.1.21
        configure()
    }
    return setOf(
        macosX64(fullConfigure),
        macosArm64(fullConfigure),
        iosArm64(fullConfigure),
        iosSimulatorArm64(fullConfigure),
        iosX64(fullConfigure),
    )
}
