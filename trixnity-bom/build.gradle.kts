plugins {
    `java-platform`
    `maven-publish`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    // platform dependencies on BOMs enable consumers to better align versions of these libraries
    api(platform(libs.ktor.bom))
    api(platform(libs.kotlinx.coroutines.bom))

    // ensure our own modules all are constrained to the same version
    constraints {
        rootProject.subprojects.forEach {
            // all publishable subprojects/modules currently are prefixed with `trixnity-`
            if (it.name.startsWith("trixnity-")) {
                it.the<PublishingExtension>().publications.forEach { publication ->
                    if (publication is MavenPublication) {
                        // TODO consider if there's a need to exclude `klib` artifacts from the BOM like kotlinx.coroutines does
                        // see https://github.com/Kotlin/kotlinx.coroutines/blob/1a0287ca3fb5d6c59594d62131e878da4929c5f8/kotlinx-coroutines-bom/build.gradle#L25
                        api(group = publication.groupId, name = publication.artifactId, version = publication.version)
                    }
                }
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
