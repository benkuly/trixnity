buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://www.jetbrains.com/intellij-repository/releases")
    }
    dependencies {
        classpath("com.squareup.sqldelight:gradle-plugin:${Versions.sqlDelight}")
    }
}

plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version Versions.dokka
    id("io.kotest.multiplatform") version Versions.kotest apply false
    id("org.kodein.mock.mockmp") version Versions.mocKmp apply false
    id("org.jetbrains.kotlinx.kover") version Versions.kotlinxKover
}

allprojects {
    group = "net.folivo"
    version = "2.2.2" +
            when {
                isRelease -> ""
                isCI -> "-SNAPSHOT"
                else -> "-LOCAL"
            }

    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }

    apply(plugin = "org.jetbrains.dokka")

    org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMultiplatformPlugin
    dependencies {
        dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${Versions.dokka}")
    }
}

subprojects {
    val dokkaJavadocJar by tasks.creating(Jar::class) {
        dependsOn(tasks.dokkaJavadoc)
        from(tasks.dokkaJavadoc.get().outputDirectory.get())
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
                        url.set("https://gitlab.com/benkuly/trixnity")
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
                            url.set("https://gitlab.com/benkuly/trixnity")
                        }

                        artifact(dokkaJavadocJar)
                    }
                }
            }
        }
        signing {
            isRequired = isCI
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