plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.realm.kotlin")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
    }
    val jvmTarget = addDefaultJvmTargetWhenEnabled()
    ios()
    iosSimulatorArm64()

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

                implementation("io.realm.kotlin:library-base:${Versions.realm}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
            }
        }
        val iosMain by getting {
        }
        jvmTarget?.mainSourceSet(sourceSets) {

        }

//        jvmTarget?.mainSourceSet(this) {
//            dependencies {
//                api("org.jetbrains.exposed:exposed-core:${Versions.exposed}")
//
//                implementation("org.jetbrains.exposed:exposed-dao:${Versions.exposed}")
//                implementation("org.jetbrains.exposed:exposed-jdbc:${Versions.exposed}")
//            }
//        }
//        jvmTarget?.testSourceSet(this) {
//            dependencies {
//                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
//                implementation("com.h2database:h2:${Versions.h2}")
//                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
//            }
//        }
    }
}
