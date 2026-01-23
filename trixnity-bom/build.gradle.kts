plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

val name = project.name

val publishedProjects = rootProject.subprojects
    .filter { it.plugins.hasPlugin(MavenPublishPlugin::class) }
    .toSet()

val otherPublishedProjects = publishedProjects - this


dependencies {
    constraints {
        otherPublishedProjects.forEach {
            api(it)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("trixnityPlatform") {
            from(components["javaPlatform"])
        }
    }
}
