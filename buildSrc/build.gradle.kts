plugins {
    // TODO workaround https://github.com/gradle/gradle/issues/16345 remove with newer gradle version
    `kotlin-dsl` version "2.4.0" // version from https://plugins.gradle.org/plugin/org.gradle.kotlin.kotlin-dsl
}

repositories {
    mavenCentral()
    google()
}

val kotlinVersion = "1.7.0" // https://github.com/JetBrains/kotlin/releases
val androidGradleVersion = "7.0.4" // https://developer.android.com/reference/tools/gradle-api

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("com.android.tools.build:gradle:$androidGradleVersion")
}