import de.undercouch.gradle.tasks.download.Download
import org.gradle.internal.os.OperatingSystem

buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:${Versions.androidGradle}")
        classpath("com.squareup.sqldelight:gradle-plugin:${Versions.sqlDelight}")
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${Versions.kotlinxAtomicfu}")
    }
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
        maven { url = uri("https://jetbrains.bintray.com/intellij-third-party-dependencies") }
    }
}

plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version Versions.dokka
    kotlin("multiplatform") version Versions.kotlin apply false
    kotlin("jvm") version Versions.kotlin apply false
    kotlin("js") version Versions.kotlin apply false
    kotlin("plugin.serialization") version Versions.kotlin apply false
    id("org.jetbrains.kotlinx.kover") version Versions.kotlinxKover
}

allprojects {
    group = "net.folivo"
    version = "1.0.0-RC1"

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

val prepareBuildOlm by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olm.root)
    if (OperatingSystem.current().isWindows) {
        // TODO we disabled tests, because the linking of them fails
        commandLine("cmake", ".", "-Bbuild", "-DCMAKE_TOOLCHAIN_FILE=Windows64.cmake", "-DOLM_TESTS=OFF")
    } else {
        commandLine("cmake", ".", "-Bbuild")
    }
    dependsOn(extractOlm)
}

val buildOlm by tasks.registering(Exec::class) {
    group = "olm"
    workingDir(olm.root)
    commandLine("cmake", "--build", "build")
    dependsOn(prepareBuildOlm)
}

subprojects {
    val dokkaJavadocJar by tasks.creating(Jar::class) {
        dependsOn(tasks.dokkaJavadoc)
        from(tasks.dokkaJavadoc.get().outputDirectory.get())
        archiveClassifier.set("javadoc")
    }

    val projectParent = parent
    if (project.name != "examples" && (projectParent == null || projectParent.name != "examples")) {
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