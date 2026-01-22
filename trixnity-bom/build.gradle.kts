plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

val name = project.name

dependencies {
    constraints {
        rootProject.subprojects.forEach subprojects@{
            if (!it.plugins.hasPlugin("maven-publish") || it.name == name || !it.name.startsWith("trixnity-")) return@subprojects
            it.the<PublishingExtension>().publications.forEach { publication ->
                if (publication !is MavenPublication) return@forEach

                val artifactId = publication.artifactId
                if (artifactId.endsWith("-metadata") || artifactId.endsWith("-kotlinMultiplatform")) {
                    return@forEach
                }

                api("${publication.groupId}:${publication.artifactId}:${publication.version}")
            }
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
