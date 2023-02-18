plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.realm.kotlin")
}

kotlin {
    jvmToolchain()
    val jvmTarget = addDefaultJvmTargetWhenEnabled()
    addAppleNativeTargetsWhenEnabled() // see https://github.com/realm/realm-kotlin/issues/617

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")

                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")

                api("io.realm.kotlin:library-base:${Versions.realm}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
            }
        }
        jvmTarget?.testSourceSet(this) {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}

// needed since gradle 8.0 and Kotlin 1.8. Reason is unknown. Should be checked if it is still needed from time to time.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = Versions.kotlinJvmTarget.majorVersion
    }
}