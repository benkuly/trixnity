import org.gradle.api.JavaVersion

object Versions {
    const val kotlin = "1.6.21" // https://github.com/JetBrains/kotlin/releases
    val kotlinJvmTarget = JavaVersion.VERSION_11
    const val ksp = "1.6.21-1.0.5" // https://github.com/google/ksp/releases
    const val kotlinxCoroutines = "1.6.1" // https://github.com/Kotlin/kotlinx.coroutines/releases
    const val kotlinxSerialization = "1.3.2" // https://github.com/Kotlin/kotlinx.serialization/releases
    const val kotlinxDatetime = "0.3.2" // https://github.com/Kotlin/kotlinx-datetime/releases
    const val kotlinxKover = "0.5.0" // https://github.com/Kotlin/kotlinx-kover/releases
    const val dokka = "1.6.20" // https://github.com/Kotlin/dokka/releases
    const val olm = "3.2.8" // https://gitlab.matrix.org/matrix-org/olm/-/releases
    const val jna = "5.11.0" // https://github.com/java-native-access/jna/tags
    const val ktor = "2.0.0" // https://github.com/ktorio/ktor/releases
    const val korlibs = "2.7.0" // https://github.com/korlibs/korge-next/releases
    const val mockative = "1.1.4" // https://github.com/mockative/mockative/releases
    const val uuid = "0.4.0" // https://github.com/benasher44/uuid/releases
    const val kotest = "5.2.3" // https://github.com/kotest/kotest/releases
    const val testContainers = "1.17.1" // https://github.com/testcontainers/testcontainers-java/releases
    const val androidGradle = "7.0.4" // https://developer.android.com/reference/tools/gradle-api
    const val androidxTestRunner = "1.4.0" // https://developer.android.com/jetpack/androidx/releases/test
    const val gradleNexusPublishPlugin = "1.1.0" // https://github.com/gradle-nexus/publish-plugin/releases
    const val arrow = "1.0.1" // https://github.com/arrow-kt/arrow/releases
    const val sqlDelight = "1.5.3" // https://github.com/cashapp/sqldelight/releases
    const val exposed = "0.38.2" // https://github.com/JetBrains/Exposed/releases
    const val h2 = "2.1.212" // https://github.com/h2database/h2database/releases
    const val thumbnailator = "0.4.17" // https://github.com/coobird/thumbnailator/tags
    const val kotlinLogging = "2.1.21" // https://github.com/MicroUtils/kotlin-logging/releases
    const val logback = "1.2.11" // https://github.com/qos-ch/logback/tags

    // make sure to update the build images, when you change a version here!
    const val androidTargetSdk = 32
    const val androidMinSdk = 26
    const val androidBuildTools = "32.0.0"
    const val androidNdk = "23.1.7779620"
    const val cmake = "3.23.1" // also update this in README.md
}