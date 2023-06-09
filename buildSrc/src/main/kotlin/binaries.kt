import org.gradle.api.Project

val trixnityBinariesTask = ":trixnityBinaries"

class TrixnityBinariesDirs(project: Project) {
    val root =
        project.rootProject.buildDir.resolve("trixnity-binaries").resolve(Versions.trixnityBinaries)

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