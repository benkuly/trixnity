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
    version = withVersionSuffix("4.3.4")

    dependencyLocking {
        lockAllConfigurations()
    }

    val dependenciesForAll by tasks.registering(DependencyReportTask::class) { }
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
            onlyIf { isCI }
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
                    if (isCI) artifact(dokkaJar)
                }
            }
        }
        signing {
            isRequired = isRelease
            useInMemoryPgpKeys(
                System.getenv("OSSRH_PGP_KEY_ID"),
                System.getenv("OSSRH_PGP_KEY"),
                System.getenv("OSSRH_PGP_PASSWORD")
            )
            sign(publishing.publications)
        }
        // Workaround for gradle issue:
        // https://github.com/gradle/gradle/issues/26132
        // https://youtrack.jetbrains.com/issue/KT-61313/Kotlin-MPP-Gradle-Signing-plugin-Task-linkDebugTestLinuxX64-uses-this-output-of-task-signLinuxX64Publication
        // https://github.com/gradle/gradle/issues/26091
        // https://youtrack.jetbrains.com/issue/KT-46466/Kotlin-MPP-publishing-Gradle-7-disables-optimizations-because-of-task-dependencies
        val signingTasks = tasks.withType<Sign>()
        tasks.withType<AbstractPublishToMaven>().configureEach {
            mustRunAfter(signingTasks)
        }
    }
}

val tmpDir = layout.buildDirectory.get().asFile.resolve("tmp")
val olmBinariesZipDir = tmpDir.resolve("trixnity-olm-binaries-${libs.versions.trixnityOlmBinaries.get()}.zip")
val opensslBinariesZipDir =
    tmpDir.resolve("trixnity-openssl-binaries-${libs.versions.trixnityOpensslBinaries.get()}.zip")
val olmBinariesDirs = TrixnityOlmBinariesDirs(project, libs.versions.trixnityOlmBinaries.get())
val opensslBinariesDirs = TrixnityOpensslBinariesDirs(project, libs.versions.trixnityOpensslBinaries.get())

val downloadOlmBinaries by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    src("https://gitlab.com/api/v4/projects/46553592/packages/generic/build/v${libs.versions.trixnityOlmBinaries.get()}/build.zip")
    dest(olmBinariesZipDir)
    overwrite(false)
}

val downloadOpensslBinaries by tasks.registering(de.undercouch.gradle.tasks.download.Download::class) {
    src("https://gitlab.com/api/v4/projects/57407788/packages/generic/build/v${libs.versions.trixnityOpensslBinaries.get()}/build.zip")
    dest(opensslBinariesZipDir)
    overwrite(false)
}

val extractOlmBinaries by tasks.registering(Copy::class) {
    from(zipTree(olmBinariesZipDir)) {
        include("build/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into(olmBinariesDirs.root)
    outputs.cacheIf { true }
    inputs.files(downloadOlmBinaries)
    dependsOn(downloadOlmBinaries)
}

val extractOpensslBinaries by tasks.registering(Copy::class) {
    from(zipTree(opensslBinariesZipDir)) {
        include("build/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into(opensslBinariesDirs.root)
    outputs.cacheIf { true }
    inputs.files(downloadOpensslBinaries)
    dependsOn(downloadOpensslBinaries)
}

val trixnityBinaries by tasks.registering {
    dependsOn(extractOlmBinaries)
    dependsOn(extractOpensslBinaries)
}

val dokkaHtmlToWebsite by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("dokka/htmlMultiModule"))
    into(layout.projectDirectory.dir("website/static/api"))
    outputs.cacheIf { true }
    dependsOn(":dokkaHtmlMultiModule")
}
