import org.gradle.api.Project

val trixnityBinariesTask = ":trixnityBinaries"

class TrixnityBinariesDirs(project: Project, version: String) {
    val root = project.rootProject.layout.buildDirectory.get().asFile
        .resolve("trixnity-binaries").resolve(version)

    val olmDir = root.resolve("olm")
    val olmHeadersDir = olmDir.resolve("headers")
    val olmBinDir = olmDir.resolve("bin")
    val olmBinSharedDir = olmBinDir.resolve("shared")
    val olmBinSharedAndroidDir = olmBinDir.resolve("shared-android")
    val olmBinStaticDir = olmBinDir.resolve("static")

    val opensslDir = root.resolve("openssl")
    val opensslHeadersDir = opensslDir.resolve("headers")
    val opensslBinDir = opensslDir.resolve("bin")
    val opensslBinStaticDir = opensslBinDir.resolve("static")
}