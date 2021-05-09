plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version Versions.dokka
    kotlin("multiplatform") version Versions.kotlin apply false
    kotlin("jvm") version Versions.kotlin apply false
    kotlin("js") version Versions.kotlin apply false
    kotlin("plugin.serialization") version Versions.kotlin apply false
}

allprojects {
    group = "net.folivo"
    version = "0.1.3"

    repositories {
        mavenCentral()
        maven("https://kotlin.bintray.com/kotlinx")
    }

    apply(plugin = "org.jetbrains.dokka")

    dependencies {
        dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${Versions.dokka}")
    }
}

inline val Project.isRelease
    get() = !version.toString().contains('-')

subprojects {
    val dokkaJavadocJar by tasks.creating(Jar::class) {
        dependsOn(tasks.dokkaJavadoc)
        from(tasks.dokkaJavadoc.get().outputDirectory.get())
        archiveClassifier.set("javadoc")
    }

    if (!project.name.startsWith("trixnity-examples")) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        publishing {
            publications.configureEach {
                if (this is MavenPublication) {
                    pom {
                        println("sub " + project.name)
                        name.set(project.name)
                        description.set("Multiplatform Kotlin SDK for matrix-protocol")
                        url.set("https://gitlab.folivo.net/benkuly/trixnity")
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
                            url.set("https://gitlab.folivo.net/benkuly/trixnity")
                        }

                        artifact(dokkaJavadocJar)
                    }
                }
            }
            repositories {
                maven {
                    name = "OSSRH"
                    url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = System.getenv("OSSRH_USERNAME")
                        password = System.getenv("OSSRH_PASSWORD")
                    }
                }
            }
        }

        signing {
            isRequired = isRelease
            useInMemoryPgpKeys(
                System.getenv("OSSRH_PGP_KEY"),
                System.getenv("OSSRH_PGP_PASSWORD")
            )
            sign(publishing.publications)
        }
    }
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(buildDir.resolve("dokkaCustomMultiModuleOutput"))
}