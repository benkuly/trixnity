import org.gradle.internal.jvm.Jvm

plugins {
    `kotlin-dsl`
}

repositories {
    val dependencyCacheUrl = System.getenv("GRADLE_DEPENDENCY_CACHE_URL")
    if (dependencyCacheUrl != null)
        maven {
            url = uri(dependencyCacheUrl)
            authentication {
                credentials {
                    username = System.getenv("GRADLE_DEPENDENCY_CACHE_USERNAME")
                    password = System.getenv("GRADLE_DEPENDENCY_CACHE_PASSWORD")
                }
            }
        }
    mavenCentral()
    google()
}

// TODO: update this when kotlin supports Java 25
val javaVersion = minOf(Jvm.current().javaVersion?.ordinal ?: 24, 24)
kotlin.jvmToolchain(javaVersion)

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin.serialization)
    implementation(libs.android.gradle.plugin)
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
}