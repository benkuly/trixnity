plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

val kotlinVersion = "1.9.20-Beta" // https://github.com/JetBrains/kotlin/releases
val androidGradleVersion = "8.1.1" // https://developer.android.com/reference/tools/gradle-api

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("com.android.tools.build:gradle:$androidGradleVersion")
}