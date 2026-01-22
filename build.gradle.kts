import de.connect2x.conventions.*
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import io.github.gradlenexus.publishplugin.NexusPublishPlugin
import kotlinx.kover.gradle.plugin.KoverGradlePlugin
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaPlugin
import java.time.Duration
import java.time.ZonedDateTime

plugins {
    alias(sharedLibs.plugins.c2xConventions)

    alias(sharedLibs.plugins.dokka)
    alias(sharedLibs.plugins.gradleNexus)
    alias(sharedLibs.plugins.kotlinx.kover)

    alias(libs.plugins.download) apply false

    builtin(sharedLibs.plugins.kotlin.multiplatform) apply false
    builtin(sharedLibs.plugins.android.library) apply false

    alias(sharedLibs.plugins.kotlin.serialization) apply false
    alias(sharedLibs.plugins.mokkery) apply false
}

allprojects {
    group = "de.connect2x"
    version = withVersionSuffix(rootProject.libs.versions.trixnity)
    if (System.getenv("WITH_LOCK")?.toBoolean() == true) defaultDependencyLocking()
    configureJava(11)
}

subprojects {
    if (!project.name.startsWith("trixnity-")) return@subprojects

    apply<MavenPublishPlugin>()
    apply<SigningPlugin>()
    apply<DokkaPlugin>()
    apply<KoverGradlePlugin>()

    extensions.configure<SigningExtension> {
        isRequired = isRelease
        signPublications()
    }

    extensions.configure<DokkaExtension> {
        moduleName = project.name
        pluginsConfiguration {
            html {
                homepageLink = "https://trixnity.connect2x.de"
                footerMessage = "&copy; ${ZonedDateTime.now().year} connect2x GmbH"
            }
        }
    }

    val javadocJar by tasks.registering(Jar::class) {
        archiveClassifier = "javadoc"
        dependsOn(tasks.dokkaGeneratePublicationHtml)
        from(tasks.dokkaGeneratePublicationHtml)
        onlyIf { isCI }
    }

    extensions.configure<PublishingExtension> {
        repositories {
            authenticatedPackageRegistry()
        }
        publications.withType<MavenPublication>().configureEach {
            pom {
                apache2()
                c2xOrganization()
                setProjectInfo(
                    name = project.name,
                    description = "Multiplatform Kotlin SDK for matrix-protocol",
                    repository = "connect2x/trixnity/trixnity",
                )
            }
            if (isCI) artifact(javadocJar)
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

dependencies {
    dokka(projects.trixnityUtils)
    dokka(projects.trixnityCore)
    dokka(projects.trixnityCrypto)
    dokka(projects.trixnityCryptoCore)
    dokka(projects.trixnityCryptoDriver)
    dokka(projects.trixnityCryptoDriver.trixnityCryptoDriverLibolm)
    dokka(projects.trixnityCryptoDriver.trixnityCryptoDriverVodozemac)
    dokka(projects.trixnityLibolm)
    dokka(projects.trixnityVodozemac)
    dokka(projects.trixnityVodozemac.trixnityVodozemacBinaries)
    dokka(projects.trixnityApiClient)
    dokka(projects.trixnityApiServer)
    dokka(projects.trixnityClientserverapi.trixnityClientserverapiModel)
    dokka(projects.trixnityClientserverapi.trixnityClientserverapiClient)
    dokka(projects.trixnityClientserverapi.trixnityClientserverapiServer)
    dokka(projects.trixnityServerserverapi.trixnityServerserverapiModel)
    dokka(projects.trixnityServerserverapi.trixnityServerserverapiClient)
    dokka(projects.trixnityServerserverapi.trixnityServerserverapiServer)
    dokka(projects.trixnityApplicationserviceapi.trixnityApplicationserviceapiModel)
    dokka(projects.trixnityApplicationserviceapi.trixnityApplicationserviceapiServer)
    dokka(projects.trixnityClient)
    dokka(projects.trixnityClient.trixnityClientMediaOkio)
    dokka(projects.trixnityClient.trixnityClientMediaIndexeddb)
    dokka(projects.trixnityClient.trixnityClientMediaOpfs)
    dokka(projects.trixnityClient.trixnityClientRepositoryExposed)
    dokka(projects.trixnityClient.trixnityClientRepositoryIndexeddb)
    dokka(projects.trixnityClient.trixnityClientRepositoryRoom)
    dokka(projects.trixnityClient.trixnityClientCryptodriverLibolm)
    dokka(projects.trixnityClient.trixnityClientCryptodriverVodozemac)
}


val dokkaHtmlToWebsite by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("website/static/api"))
    outputs.cacheIf { true }
    dependsOn(":dokkaGenerate")
}

registerCoverageTask()

kover {
    dependencies {
        fun DependencyHandler.addSubProjectsWithKoverAsDependency(project: Project) {
            for (subProject in project.subprojects) {
                subProject.afterEvaluate {
                    if (subProject.plugins.hasPlugin("org.jetbrains.kotlinx.kover"))
                        kover(subProject)
                }
                addSubProjectsWithKoverAsDependency(subProject)
            }
        }

        addSubProjectsWithKoverAsDependency(project)
    }
    reports {
        filters {
            includes.classes("de.connect2x.trixnity.*")
        }
    }
}

nexusPublishing {
    authenticatedSonatype()
    connectTimeout = Duration.ofSeconds(30)
    clientTimeout = Duration.ofMinutes(45)
}