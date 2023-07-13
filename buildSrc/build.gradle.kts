plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

val kotlinVersion = "1.8.22" // https://github.com/JetBrains/kotlin/releases
val androidGradleVersion = "7.4.2" // https://developer.android.com/reference/tools/gradle-api

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
    implementation("com.android.tools.build:gradle:$androidGradleVersion")
}