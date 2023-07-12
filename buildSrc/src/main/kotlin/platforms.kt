import org.jetbrains.kotlin.konan.target.HostManager

val isCI = System.getenv("CI") != null
val isMainCIHost = isCI && HostManager.hostIsLinux
val isRelease = System.getenv("CI_COMMIT_TAG")?.matches("^v\\d+.\\d+.\\d+.*".toRegex()) ?: false
fun checkSameReleaseVersion(version: String) {
    if (isRelease) check(version == System.getenv("CI_COMMIT_TAG").removePrefix("v"))
}

fun withVersionSuffix(version: String) = (version + when {
    isRelease -> ""
    isCI -> "-SNAPSHOT"
    else -> "-LOCAL"
}).also { checkSameReleaseVersion(it) }