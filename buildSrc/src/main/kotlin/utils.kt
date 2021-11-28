import org.gradle.api.Project

inline val Project.isRelease
    get() = System.getenv("CI") != null