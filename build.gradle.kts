plugins {
    `maven-publish`
    signing
}

allprojects {
    group = "net.folivo"
    version = "1.0.0"
}

inline val Project.isRelease
    get() = !version.toString().contains('-')

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    repositories {
        mavenCentral()
        jcenter()
    }

    val mavenPublication = publishing.publications.create<MavenPublication>(project.name) {
        pom {
            artifactId = project.name

            name.set(project.name)
            description.set("Multiplatform Kotlin SDK for matrix-protocol")
            url.set("https://github.com/benkuly/trixnity")
            licenses {
                license {
                    name.set("GNU Affero General Public License, Version 3.0")
                    url.set("http://www.gnu.org/licenses/agpl-3.0.de.html")
                }
            }
            developers {
                developer {
                    id.set("benkuly")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/benkuly/trixnity.git")
                developerConnection.set("scm:git:ssh://github.com/benkuly/trixnity.git")
                url.set("https://github.com/benkuly/trixnity")
            }
        }
    }


    val sontatypeRepository = publishing.repositories.maven {
        name = "OSSRH"
        url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
        credentials {
            username = System.getenv("OSSRH_USERNAME")
            password = System.getenv("OSSRH_PASSWORD")
        }
    }

    signing {
        isRequired = isRelease
        useInMemoryPgpKeys(
            System.getenv("OSSRH_GPG_KEY"),
            System.getenv("OSSRH_GPG_PASSWORD")
        )
        sign(mavenPublication)
    }

    project.tasks.withType<PublishToMavenRepository>().configureEach {
        publication = mavenPublication
        repository = sontatypeRepository
    }
}