import org.gradle.api.JavaVersion

object Versions {
    val kotlinJvmTarget = JavaVersion.VERSION_11
    const val kotlinxCoroutines = "1.6.4" // https://github.com/Kotlin/kotlinx.coroutines/releases
    const val kotlinxSerialization = "1.4.1" // https://github.com/Kotlin/kotlinx.serialization/releases
    const val kotlinxDatetime = "0.4.0" // https://github.com/Kotlin/kotlinx-datetime/releases
    const val dokka = "1.7.20" // https://github.com/Kotlin/dokka/releases
    const val olm = "3.2.13" // https://gitlab.matrix.org/matrix-org/olm/-/releases
    const val olmBinaries = "3.2.13-1" // https://gitlab.com/trixnity/olm-binaries/-/releases
    const val jna = "5.12.1" // https://github.com/java-native-access/jna/tags
    const val ktor = "2.2.1" // https://github.com/ktorio/ktor/releases
    const val koin = "3.2.2" // https://github.com/InsertKoinIO/koin/tags
    const val korlibs = "3.4.0" // https://github.com/korlibs/korge/releases
    const val mocKmp = "1.11.0" // https://github.com/Kodein-Framework/MocKMP/releases
    const val okio = "3.2.0" // https://square.github.io/okio/changelog/
    const val uuid = "0.6.0" // https://github.com/benasher44/uuid/releases
    const val kotest = "5.5.4" // https://github.com/kotest/kotest/releases
    const val testContainers = "1.17.6" // https://github.com/testcontainers/testcontainers-java/releases
    const val androidxTestRunner = "1.4.0" // https://developer.android.com/jetpack/androidx/releases/test
    const val arrow = "1.1.3" // https://github.com/arrow-kt/arrow/releases
    const val sqlDelight = "1.5.4" // https://github.com/cashapp/sqldelight/releases
    const val exposed = "0.41.1" // https://github.com/JetBrains/Exposed/releases
    const val juulLabsIndexeddb = "0.5.0" // https://github.com/JuulLabs/indexeddb/releases
    const val h2 = "2.1.214" // https://github.com/h2database/h2database/releases
    const val realm = "1.5.1" // https://github.com/realm/realm-kotlin/tags
    const val kotlinLogging = "2.1.23" // https://github.com/MicroUtils/kotlin-logging/releases
    const val logback = "1.2.11" // https://github.com/qos-ch/logback/tags
    const val downloadGradlePlugin = "5.3.0" // https://github.com/michel-kraemer/gradle-download-task/releases

    // make sure to update the build images, when you change a version here!
    const val androidTargetSdk = 33
    const val androidMinSdk = 24
    const val androidBuildTools = "33.0.0"
}