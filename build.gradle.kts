import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.konan.target.HostManager

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:${Versions.androidGradle}")
        classpath("com.squareup.sqldelight:gradle-plugin:${Versions.sqlDelight}")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${Versions.kotlinxAtomicfu}")
    }
}

plugins {
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version Versions.gradleNexusPublishPlugin
    id("org.jetbrains.dokka") version Versions.dokka
    kotlin("multiplatform") version Versions.kotlin apply false
    kotlin("jvm") version Versions.kotlin apply false
    kotlin("js") version Versions.kotlin apply false
    kotlin("plugin.serialization") version Versions.kotlin apply false
    id("org.jetbrains.kotlinx.kover") version Versions.kotlinxKover
}

allprojects {
    group = "net.folivo"
    version = "1.1.8"

    repositories {
        mavenCentral()
        google()
    }

    apply(plugin = "org.jetbrains.dokka")

    dependencies {
        dokkaHtmlPlugin("org.jetbrains.dokka:kotlin-as-java-plugin:${Versions.dokka}")
    }
}

val downloadOlm by tasks.registering(Download::class) {
    group = "olm"
    src("https://gitlab.matrix.org/matrix-org/olm/-/archive/${Versions.olm}/olm-${Versions.olm}.zip")
    dest(olm.zip)
    overwrite(false)
}

val extractOlm by tasks.registering(Copy::class) {
    group = "olm"
    from(zipTree(olm.zip)) {
        include("olm-${Versions.olm}/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into(olm.root)
    dependsOn(downloadOlm)
}

val prepareBuildOlmWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olm.root)
    // TODO we disabled tests, because the linking of them fails
    commandLine("cmake", ".", "-BbuildWin", "-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake", "-DOLM_TESTS=OFF")
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olm.cMakeLists)
    outputs.dir(olm.buildWin)
}

val buildOlmWindows by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olm.root)
    commandLine("cmake", "--build", "buildWin")
    dependsOn(prepareBuildOlmWindows)
    outputs.cacheIf { true }
    inputs.files(olm.cMakeLists)
    outputs.dir(olm.buildWin)
}

val prepareBuildOlmLinux by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olm.root)
    commandLine("cmake", ".", "-Bbuild")
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(extractOlm)
    outputs.cacheIf { true }
    inputs.files(olm.cMakeLists)
    outputs.dir(olm.build)
}

val buildOlmLinux by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olm.root)
    commandLine("cmake", "--build", "build")
    onlyIf { !HostManager.hostIsMingw }
    dependsOn(prepareBuildOlmLinux)
    outputs.cacheIf { true }
    inputs.files(olm.cMakeLists)
    outputs.dir(olm.build)
}

val buildOlm by tasks.registering {
    dependsOn(buildOlmLinux, buildOlmWindows)
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
            isRequired = isRelease
            useInMemoryPgpKeys(
                System.getenv("OSSRH_PGP_KEY"),
                System.getenv("OSSRH_PGP_PASSWORD")
            )
            sign(publishing.publications)
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            username.set(System.getenv("OSSRH_USERNAME"))
            password.set(System.getenv("OSSRH_PASSWORD"))
        }
    }
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(buildDir.resolve("dokkaCustomMultiModuleOutput"))
}