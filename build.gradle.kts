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
    id("com.google.devtools.ksp") version Versions.ksp apply false
    id("org.jetbrains.kotlinx.kover") version Versions.kotlinxKover
}

allprojects {
    group = "net.folivo"
    version = "1.2.0-RC2"

    repositories {
        // TODO remove when ktor 2.0.0 or another beta is released
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        // TODO remove when ktor 2.0.0 uses stable or RC kotlin version (see https://github.com/ktorio/ktor/blob/main/gradle.properties#L23)
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/") }
        mavenCentral()
        google()
    }

    apply(plugin = "org.jetbrains.dokka")

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
