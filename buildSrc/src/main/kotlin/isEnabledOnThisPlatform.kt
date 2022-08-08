import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

val isCI = System.getenv("CI") != null
val isRelease = System.getenv("CI_COMMIT_TAG")?.matches("^v\\d+.\\d+.\\d+.*".toRegex()) ?: false

val isAndroidEnabled = KotlinPlatformType.androidJvm.isEnabledOnThisPlatform()

enum class CIRequiredHostType {
    LINUX, MAC, COMMON
}

fun CIRequiredHostType.isEnabledOnThisPlatform() =
    when (this) {
        CIRequiredHostType.LINUX -> HostManager.hostIsLinux
        CIRequiredHostType.MAC -> HostManager.hostIsMac
        CIRequiredHostType.COMMON -> true
    }

fun Family.isEnabledOnThisPlatform(): Boolean =
    when (this) {
        Family.OSX,
        Family.IOS,
        Family.TVOS,
        Family.WATCHOS -> CIRequiredHostType.MAC

        Family.LINUX,
        Family.ANDROID,
        Family.WASM,
        Family.MINGW -> if (isCI) CIRequiredHostType.LINUX else CIRequiredHostType.COMMON

        Family.ZEPHYR -> error("Unsupported family: $this")
    }.isEnabledOnThisPlatform()

fun KonanTarget.isEnabledOnThisPlatform(): Boolean = this.family.isEnabledOnThisPlatform()

fun KotlinTarget.isEnabledOnThisPlatform(): Boolean =
    if (platformType == KotlinPlatformType.native) (this as KotlinNativeTarget).konanTarget.isEnabledOnThisPlatform()
    else platformType.isEnabledOnThisPlatform()

fun KotlinPlatformType.isEnabledOnThisPlatform(): Boolean = when (this) {
    KotlinPlatformType.common,
    KotlinPlatformType.jvm -> CIRequiredHostType.COMMON

    KotlinPlatformType.androidJvm,
    KotlinPlatformType.wasm,
    KotlinPlatformType.js -> if (isCI) CIRequiredHostType.LINUX else CIRequiredHostType.COMMON

    KotlinPlatformType.native -> throw IllegalArgumentException("Cannot determine enabled state for native. Don't use KotlinPlatformType for it.")
}.isEnabledOnThisPlatform()