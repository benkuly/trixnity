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
    id("com.google.devtools.ksp") version Versions.ksp apply false
    id("io.realm.kotlin") version Versions.realm apply false
    id("de.undercouch.download") version Versions.downloadGradlePlugin apply false
}

allprojects {
    group = "net.folivo"
    version = withVersionSuffix("3.7.0")

    repositories {
        mavenCentral()
        google()
        mavenLocal()
    }

    apply(plugin = "org.jetbrains.dokka")
}

subprojects {
    val dokkaJar by tasks.registering(Jar::class) {
        dependsOn(tasks.dokkaHtml)
        from(tasks.dokkaHtml.flatMap { it.outputDirectory })
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
                    }
                    artifact(dokkaJar)
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

val tmpDir = buildDir.resolve("tmp")
val trixnityBinariesZipDir = tmpDir.resolve("trixnity-binaries-${Versions.trixnityBinaries}.zip")
val trixnityBinariesDirs = TrixnityBinariesDirs(project)

val downloadTrixnityBinaries by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    src("https://gitlab.com/api/v4/projects/46553592/packages/generic/build/v${Versions.trixnityBinaries}/build.zip")
    dest(trixnityBinariesZipDir)
    overwrite(false)
}

val extractTrixnityBinaries by tasks.registering(Copy::class) {
    from(zipTree(trixnityBinariesZipDir)) {
        include("build/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into(trixnityBinariesDirs.root)
    outputs.cacheIf { true }
    inputs.files(downloadTrixnityBinaries)
    dependsOn(downloadTrixnityBinaries)
}

val trixnityBinaries by tasks.registering {
    dependsOn(extractTrixnityBinaries)
}