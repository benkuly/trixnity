buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin.api)
    }
}

plugins {
    `maven-publish`
    signing
    id(libs.plugins.dokka.get().pluginId)
    alias(libs.plugins.realm).apply(false)
    alias(libs.plugins.download).apply(false)
    alias(libs.plugins.kotest).apply(false)
    alias(libs.plugins.mokkery).apply(false)
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
    dokka(projects.trixnityCryptoCore)
    dokka(projects.trixnityCrypto)
    dokka(projects.trixnityOlm)
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
    dokka(projects.trixnityClient.trixnityClientRepositoryRealm)
    dokka(projects.trixnityClient.trixnityClientRepositoryRoom)
    dokka(projects.trixnityApplicationservice)
}

val dokkaHtmlToWebsite by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("website/static/api"))
    outputs.cacheIf { true }
    dependsOn(":dokkaGenerate")
}
