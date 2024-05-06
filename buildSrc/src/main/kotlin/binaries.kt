import org.gradle.api.Project
import org.jetbrains.kotlin.konan.target.KonanTarget

val trixnityBinariesTask = ":trixnityBinaries"

class TrixnityOlmBinariesDirs(project: Project, version: String) {
    val root = project.rootProject.layout.buildDirectory.get().asFile
        .resolve("trixnity-olm-binaries").resolve(version)

    private val rootDir = root.resolve("olm")
    val headers = rootDir.resolve("headers")
    private val binDir = rootDir.resolve("bin")
    val binShared = binDir.resolve("shared")
    val binSharedAndroid = binDir.resolve("shared-android")
    val binStatic = binDir.resolve("static")
}

class TrixnityOpensslBinariesDirs(project: Project, version: String) {
    val root = project.rootProject.layout.buildDirectory.get().asFile
        .resolve("trixnity-openssl-binaries").resolve(version)

    private val rootDir = root.resolve("openssl")
    private fun targetDir(target: KonanTarget) = rootDir.resolve(target.name)
    fun include(target: KonanTarget) = targetDir(target).resolve("include")
    fun lib(target: KonanTarget) = targetDir(target).resolve("lib")
}