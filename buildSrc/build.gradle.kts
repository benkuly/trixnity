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

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin.serialization)
    implementation(libs.android.gradle.plugin)
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
}