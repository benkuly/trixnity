import org.gradle.api.Project
import java.io.File

class OlmDirs(buildDir: File) {
    val root = buildDir.resolve("olm").resolve(Versions.olm)
    val zip = buildDir.resolve("tmp").resolve("olm-${Versions.olm}.zip")
    val build = root.resolve("build")
    val include = root.resolve("include")
}

inline val Project.olm: OlmDirs
    get() = OlmDirs(rootProject.buildDir)