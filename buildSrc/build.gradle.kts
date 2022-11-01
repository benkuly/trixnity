plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

val kotlinVersion = "1.7.20" // https://github.com/JetBrains/kotlin/releases
val androidGradleVersion = "7.3.1" // https://developer.android.com/reference/tools/gradle-api

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("com.android.tools.build:gradle:$androidGradleVersion")
}