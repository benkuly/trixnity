plugins {
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
}

val dokkaJar by tasks.registering(Jar::class) {
    dependsOn("dokkaGenerate")
    from(dokka.dokkaPublications.html.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
    onlyIf { isCI }
}

publishing {
    repositories {
        maven {
            url = uri(rootProject.layout.buildDirectory.map { it.dir("maven-central-bundle") })
            name = "Central"
        }
        maven {
            url = uri("${System.getenv("CI_API_V4_URL")}/projects/26519650/packages/maven")
            name = "GitLab"
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
                url.set("https://gitlab.com/connect2x/trixnity/trixnity")
                inceptionYear.set("2021")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("benkuly")
                        name.set("benkuly")
                    }
                }
                scm {
                    url.set("https://gitlab.com/connect2x/trixnity/trixnity")
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

fun Project.publishing(configure: Action<PublishingExtension>) = extensions.configure("publishing", configure)

fun Project.signing(configure: Action<SigningExtension>) = extensions.configure("signing", configure)

val Project.publishing: PublishingExtension
    get() = extensions.getByName("publishing") as PublishingExtension
