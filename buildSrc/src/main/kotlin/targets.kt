import com.android.build.gradle.internal.coverage.JacocoReportTask.JacocoReportWorkerAction.Companion.logger
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

fun <T : KotlinTarget> KotlinMultiplatformExtension.addTargetWhenEnabled(
    target: KotlinPlatformType,
    createTarget: KotlinTargetContainerWithNativeShortcuts.() -> T,
): T? =
    when {
        isMainCIHost -> createTarget().apply {
            compilations.configureEach {
                compileTaskProvider.get().enabled = target.isEnabledOnThisPlatform()
            }
        }

        isCI && target.isEnabledOnThisPlatform() || isCI.not() -> createTarget()
        else -> {
            logger.info("disabled target ${target.name} because it is not enabled on this platform")
            null
        }
    }

fun <T : KotlinNativeTarget> KotlinMultiplatformExtension.addNativeTargetWhenEnabled(
    target: KonanTarget,
    ignoreCIState: Boolean = false,
    createTarget: KotlinTargetContainerWithNativeShortcuts.() -> T,
): T? {
    val isCI = ignoreCIState || isCI
    return when {
        isMainCIHost -> createTarget().apply {
            compilations.configureEach {
                compileTaskProvider.get().enabled = target.isEnabledOnThisPlatform()
            }
        }

        isCI && target.isEnabledOnThisPlatform() || isCI.not() -> createTarget()
        else -> {
            logger.info("disabled native target ${target.name} because it is not enabled on this platform")
            null
        }
    }
}

fun KotlinTarget.mainSourceSet(
    sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
    configure: KotlinSourceSet.() -> Unit = {}
): KotlinSourceSet =
    sourceSets.getByName(targetName + "Main").apply(configure)

fun KotlinTarget.testSourceSet(
    sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
    configure: KotlinSourceSet.() -> Unit
): KotlinSourceSet =
    sourceSets.getByName(targetName + "Test").apply(configure)

fun KotlinAndroidTarget.testSourceSet(
    sourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
    configure: KotlinSourceSet.() -> Unit
): KotlinSourceSet =
    sourceSets.getByName(targetName + "UnitTest").apply(configure)

fun KotlinMultiplatformExtension.addDefaultJvmTargetWhenEnabled(
    useJUnitPlatform: Boolean = true,
    testEnabled: Boolean = true
): KotlinJvmTarget? =
    addTargetWhenEnabled(KotlinPlatformType.jvm) {
        jvm {
            compilations.all {
                kotlinOptions.jvmTarget = Versions.kotlinJvmTarget.toString()
            }
            testRuns["test"].executionTask.configure {
                enabled = testEnabled
                if (useJUnitPlatform) useJUnitPlatform()
            }
        }
    }

fun KotlinMultiplatformExtension.ciDummyTarget() {
    if (isCI)
        jvm {
            compilations.all {
                kotlinOptions.jvmTarget = Versions.kotlinJvmTarget.toString()
            }
            testRuns["test"].executionTask.configure {
                enabled = false
            }
        }
}

fun KotlinMultiplatformExtension.addDefaultJsTargetWhenEnabled(
    rootDir: File,
    testEnabled: Boolean = true,
    browserEnabled: Boolean = true,
    nodeJsEnabled: Boolean = true,
): KotlinJsTargetDsl? =
    addTargetWhenEnabled(KotlinPlatformType.js) {
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
    }

fun KotlinMultiplatformExtension.addDefaultNativeTargetsWhenEnabled(): Set<KotlinNativeTarget> =
    addDesktopNativeTargetsWhenEnabled() + addAppleNativeTargetsWhenEnabled()

fun KotlinMultiplatformExtension.addDesktopNativeTargetsWhenEnabled(): Set<KotlinNativeTarget> =
    setOfNotNull(
        addNativeTargetWhenEnabled(KonanTarget.LINUX_X64) { linuxX64() },
        addNativeTargetWhenEnabled(KonanTarget.MINGW_X64) { mingwX64() },
    )

fun KotlinMultiplatformExtension.addAppleNativeTargetsWhenEnabled(): Set<KotlinNativeTarget> =
    setOfNotNull(
        addNativeTargetWhenEnabled(KonanTarget.MACOS_X64) { macosX64() },
        addNativeTargetWhenEnabled(KonanTarget.MACOS_ARM64) { macosArm64() },
        addNativeTargetWhenEnabled(KonanTarget.IOS_ARM64) { iosArm64() },
        addNativeTargetWhenEnabled(KonanTarget.IOS_SIMULATOR_ARM64) { iosSimulatorArm64() },
        addNativeTargetWhenEnabled(KonanTarget.IOS_X64) { iosX64() },
    )