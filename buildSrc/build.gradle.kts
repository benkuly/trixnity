plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

val kotlinVersion = "1.7.10" // https://github.com/JetBrains/kotlin/releases
val androidGradleVersion = "7.0.4" // https://developer.android.com/reference/tools/gradle-api

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("com.android.tools.build:gradle:$androidGradleVersion")
}