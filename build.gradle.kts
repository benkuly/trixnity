buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin.api)
    }
}

plugins {
    `maven-publish`
    signing
    alias(libs.plugins.dokka)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.realm).apply(false)
    alias(libs.plugins.download).apply(false)
    alias(libs.plugins.kotest).apply(false)
}

allprojects {
    group = "net.folivo"
    version = withVersionSuffix("4.0.0")
}

subprojects {
    if (project.name.startsWith("trixnity-")) {
        apply(plugin = "org.jetbrains.dokka")
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        val dokkaJar by tasks.registering(Jar::class) {
            dependsOn(tasks.dokkaHtml)
            from(tasks.dokkaHtml.flatMap { it.outputDirectory })
            archiveClassifier.set("javadoc")
        }

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
                    url = uri("${System.getenv("CI_API_V4_URL")}/projects/26519650/packages/maven")
                    name = "Snapshot"
                    credentials(HttpHeaderCredentials::class) {
                        name = "Job-Token"
                        value = System.getenv("CI_JOB_TOKEN")
                    }
                    authentication {
                        create("header", HttpHeaderAuthentication::class)
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
    // Workaround for gradle issue: https://github.com/gradle/gradle/issues/26091
    val signingTasks = tasks.withType<Sign>()
    tasks.withType<AbstractPublishToMaven>().configureEach {
        mustRunAfter(signingTasks)
    }
}


val tmpDir = layout.buildDirectory.get().asFile.resolve("tmp")
val trixnityBinariesZipDir = tmpDir.resolve("trixnity-binaries-${libs.versions.trixnityBinaries.get()}.zip")
val trixnityBinariesDirs = TrixnityBinariesDirs(project, libs.versions.trixnityBinaries.get())

val downloadTrixnityBinaries by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    src("https://gitlab.com/api/v4/projects/46553592/packages/generic/build/v${libs.versions.trixnityBinaries.get()}/build.zip")
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