object Versions {
    const val kotlin = "1.5.31" // https://kotlinlang.org/
    const val kotlinxCoroutines = "1.5.2-native-mt" // https://github.com/Kotlin/kotlinx.coroutines/releases
    const val kotlinxSerializationJson = "1.3.1" // https://github.com/Kotlin/kotlinx.serialization/releases
    const val kotlinxDatetime = "0.3.1" // https://github.com/Kotlin/kotlinx-datetime/releases
    const val kotlinxAtomicfu = "0.16.3" // https://github.com/Kotlin/kotlinx.atomicfu/releases
    const val kotlinxKover = "0.4.4" // https://github.com/Kotlin/kotlinx-kover/releases
    const val dokka = "1.5.31" // https://github.com/Kotlin/dokka/releases
    const val olm = "3.2.8" // https://gitlab.matrix.org/matrix-org/olm/-/releases
    const val jna = "5.10.0" // https://github.com/java-native-access/jna/releases
    const val ktor = "1.6.5" // https://github.com/ktorio/ktor/releases
    const val mockk = "1.12.1" // https://github.com/mockk/mockk/releases
    const val uuid = "0.3.1" // https://github.com/benasher44/uuid/releases
    const val kotest = "4.6.4" // https://github.com/kotest/kotest/releases
    const val testContainers = "1.16.2" // https://github.com/testcontainers/testcontainers-java/releases
    const val kodeinLog = "0.11.1" // https://github.com/Kodein-Framework/Kodein-Log/releases
    const val androidGradle = "7.0.4" // https://developer.android.com/reference/tools/gradle-api
    const val arrow = "1.0.1" // https://github.com/arrow-kt/arrow/releases
    const val sqlDelight = "1.5.3" // https://github.com/cashapp/sqldelight/releases
    const val exposed = "0.36.2" // https://github.com/JetBrains/Exposed/releases
    const val liquibase = "4.6.2" // https://github.com/liquibase/liquibase/releases
    const val snakeyml = "1.30" // https://search.maven.org/artifact/org.yaml/snakeyaml
    const val h2 = "1.4.200" // https://github.com/h2database/h2database/releases
    const val thumbnailator = "0.4.15" // https://github.com/coobird/thumbnailator/releases
    const val logback = "1.2.7" // https://github.com/qos-ch/logback/tags

    // make sure to update the build images, when you change a version here!
    const val androidTargetSdk = 30
    const val androidMinSdk = 26
    const val androidBuildTools = "30.0.3"
    const val androidNdk = "23.1.7779620"
    const val cmake = "3.22.1" // update this in README.md
}