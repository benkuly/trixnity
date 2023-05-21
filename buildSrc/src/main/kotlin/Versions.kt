import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

object Versions {
    val kotlinJvmTarget = JavaVersion.VERSION_11
    const val ksp = "1.8.21-1.0.11" // https://github.com/google/ksp/releases
    const val kotlinxCoroutines = "1.7.1" // https://github.com/Kotlin/kotlinx.coroutines/releases
    const val kotlinxSerialization = "1.5.1" // https://github.com/Kotlin/kotlinx.serialization/releases
    const val kotlinxDatetime = "0.4.0" // https://github.com/Kotlin/kotlinx-datetime/releases
    const val dokka = "1.8.10" // https://github.com/Kotlin/dokka/releases
    const val olm = "3.2.13" // https://gitlab.matrix.org/matrix-org/olm/-/releases
    const val olmBinaries = "3.2.13-1" // https://gitlab.com/trixnity/olm-binaries/-/releases
    const val jna = "5.13.0" // https://github.com/java-native-access/jna/tags
    const val ktor = "2.3.0" // https://github.com/ktorio/ktor/releases
    const val koin = "3.4.0" // https://github.com/InsertKoinIO/koin/tags
    const val korlibs = "4.0.0" // https://github.com/korlibs/korge/releases
    const val mocKmp = "1.14.0" // https://github.com/Kodein-Framework/MocKMP/releases
    const val okio = "3.3.0" // https://square.github.io/okio/changelog/
    const val uuid = "0.7.0" // https://github.com/benasher44/uuid/releases
    const val kotest = "5.6.2" // https://github.com/kotest/kotest/releases
    const val testContainers = "1.18.1" // https://github.com/testcontainers/testcontainers-java/releases
    const val androidxTestRunner = "1.4.0" // https://developer.android.com/jetpack/androidx/releases/test
    const val arrow = "1.1.5" // https://github.com/arrow-kt/arrow/releases
    const val exposed = "0.41.1" // https://github.com/JetBrains/Exposed/releases
    const val juulLabsIndexeddb = "0.6.0" // https://github.com/JuulLabs/indexeddb/releases
    const val h2 = "2.1.214" // https://github.com/h2database/h2database/releases
    const val realm = "1.8.0" // https://github.com/realm/realm-kotlin/tags
    const val androidxRoom = "2.5.1" // https://developer.android.com/jetpack/androidx/releases/room
    const val androidxTextKtx = "1.5.0" // https://developer.android.com/jetpack/androidx/releases/test
    const val robolectric = "4.9.2" // https://github.com/robolectric/robolectric
    const val downloadGradlePlugin = "5.4.0" // https://github.com/michel-kraemer/gradle-download-task/releases
    const val completeKotlinPlugin = "1.1.0" // https://github.com/LouisCAD/CompleteKotlin/releases

    // upgrade only, as soon as https://github.com/tony19/logback-android/issues/249 is resolved
    const val kotlinLogging = "2.1.23" // https://github.com/MicroUtils/kotlin-logging/releases
    const val logback = "1.2.11" // https://github.com/qos-ch/logback/tags

    // make sure to update the build images, when you change a version here!
    const val androidTargetSdk = 33
    const val androidMinSdk = 24
    const val androidBuildTools = "33.0.0"
}

val JavaVersion.number: Int
    get() = JavaLanguageVersion.of(this.majorVersion).asInt()

fun KotlinMultiplatformExtension.jvmToolchain() = jvmToolchain(Versions.kotlinJvmTarget.number)