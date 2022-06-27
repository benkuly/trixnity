import org.gradle.api.NamedDomainObjectCollection
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

val isCI = System.getenv("CI") != null
val isRelease = System.getenv("CI_COMMIT_TAG")?.matches("/^v\\d+.\\d+.\\d+.*/".toRegex()) ?: false

enum class CIHostType {
    LINUX, MAC
}

fun CIHostType.isCompilationAllowed() =
    when (this) {
        CIHostType.LINUX -> HostManager.hostIsLinux
        CIHostType.MAC -> HostManager.hostIsMac
    }

fun Family.isCompilationAllowed(): Boolean =
    when (this) {
        Family.OSX,
        Family.IOS,
        Family.TVOS,
        Family.WATCHOS -> CIHostType.MAC
        Family.LINUX,
        Family.ANDROID,
        Family.WASM,
        Family.MINGW -> CIHostType.LINUX
        Family.ZEPHYR -> error("Unsupported family: $this")
    }.isCompilationAllowed()

fun KonanTarget.isCompilationAllowed(): Boolean = this.family.isCompilationAllowed()

fun KotlinTarget.isCompilationAllowed(): Boolean {
    return when (platformType) {
        KotlinPlatformType.common,
        KotlinPlatformType.androidJvm,
        KotlinPlatformType.jvm,
        KotlinPlatformType.js -> CIHostType.LINUX
        KotlinPlatformType.native -> return (this as KotlinNativeTarget).konanTarget.isCompilationAllowed()
        KotlinPlatformType.wasm -> CIHostType.LINUX
    }.isCompilationAllowed()
}

fun NamedDomainObjectCollection<KotlinTarget>.disableCompilationsOnCI() {
    if (isCI)
        configureEach {
            if (isCompilationAllowed().not()) {
                compilations.configureEach {
                    compileKotlinTask.enabled = false
                }
            }
        }
}