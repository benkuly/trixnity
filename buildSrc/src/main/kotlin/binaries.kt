import org.gradle.api.Project

val trixnityBinariesTask = ":trixnityBinaries"

class TrixnityBinariesDirs(project: Project) {
    val root =
        project.rootProject.buildDir.resolve("trixnity-binaries").resolve(Versions.trixnityBinaries)
    val trixnityBinariesHeadersDir = root.resolve("headers")

    val olmBinDir = root.resolve("bin").resolve("olm")
    val olmBinSharedDir = olmBinDir.resolve("shared")
    val olmBinSharedAndroidDir = olmBinDir.resolve("shared-android")
    val olmBinStaticDir = olmBinDir.resolve("static")

    val opensslBinDir = root.resolve("bin").resolve("openssl")
    val opensslBinStaticDir = opensslBinDir.resolve("static")
}