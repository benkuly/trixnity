import org.gradle.api.Project

inline val Project.isRelease
    get() = !version.toString().contains('-')