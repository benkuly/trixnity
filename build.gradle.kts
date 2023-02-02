buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://www.jetbrains.com/intellij-repository/releases")
    }
}

plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version Versions.dokka
    id("io.kotest.multiplatform") version Versions.kotest apply false
    id("org.kodein.mock.mockmp") version Versions.mocKmp apply false
    id("io.realm.kotlin") version Versions.realm apply false
}

allprojects {
    group = "net.folivo"
    version = withVersionSuffix("3.3.0")

    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }

    apply(plugin = "org.jetbrains.dokka")
}

subprojects {
    val dokkaJar by tasks.creating(Jar::class) {
        dependsOn(tasks.dokkaHtml)
        from(tasks.dokkaHtml)
        archiveClassifier.set("javadoc")
    }

    if (project.name.startsWith("trixnity-")) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        publishing {
            repositories {
                maven {
                    name = "Release"
                    val repositoryId = System.getenv("OSSRH_REPOSITORY_ID")
                    url = uri("https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId")
                    credentials {
                        username = System.getenv("OSSRH_USERNAME")
                        password = System.getenv("OSSRH_PASSWORD")
                    }
                }
                maven {
                    name = "Snapshot"
                    url = uri("https://oss.sonatype.org/content/repositories/snapshots")
                    credentials {
                        username = System.getenv("OSSRH_USERNAME")
                        password = System.getenv("OSSRH_PASSWORD")
                    }
                }
            }
            publications.configureEach {
                if (this is MavenPublication) {
                    pom {
                        name.set(project.name)
                        description.set("Multiplatform Kotlin SDK for matrix-protocol")
                        url.set("https://gitlab.com/trixnity/trixnity")
                        licenses {
                            license {
                                name.set("Apache License 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                        developers {
                            developer {
                                id.set("benkuly")
                            }
                        }
                        scm {
                            url.set("https://gitlab.com/trixnity/trixnity")
                        }

                        artifact(dokkaJar)
                    }
                }
            }
        }
        signing {
            isRequired = isCI
            useInMemoryPgpKeys(
                System.getenv("OSSRH_PGP_KEY_ID"),
                System.getenv("OSSRH_PGP_KEY"),
                System.getenv("OSSRH_PGP_PASSWORD")
            )
            sign(publishing.publications)
        }
    }
    tasks.withType<AbstractPublishToMaven>().configureEach {
        onlyIf {
            publication.artifactId.contains("dummy").not()
        }
    }
}