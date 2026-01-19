rootProject.name = "trixnity-buildSrc"

dependencyResolutionManagement {
    repositories {
        maven("https://gitlab.com/api/v4/projects/68438621/packages/maven") // c2x Conventions
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/68438621/packages/maven") // c2x Conventions
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
    id("de.connect2x.conventions.c2x-settings-plugin")
}
