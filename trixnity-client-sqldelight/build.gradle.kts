plugins {
    kotlin("plugin.serialization")
    kotlin("multiplatform")
    id("com.squareup.sqldelight")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            systemProperty("java.library.path", olm.build.canonicalPath)
            systemProperty("jna.library.path", olm.build.canonicalPath)
        }
    }
//    js {
//        browser {
//            testTask {
//                useKarma {
//                    useFirefoxHeadless()
//                }
//            }
//        }
//        binaries.executable()
//    }
//    val hostOs = System.getProperty("os.name")
//    val isMingwX64 = hostOs.startsWith("Windows")
//    val nativeTarget = when {
//        hostOs == "Mac OS X" -> macosX64("native")
//        hostOs == "Linux" -> linuxX64("native")
//        isMingwX64 -> mingwX64("native")
//        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
//    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                implementation("com.squareup.sqldelight:coroutines-extensions:${Versions.sqlDelight}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                api("org.kodein.log:kodein-log:${Versions.kodeinLog}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.mockk:mockk:${Versions.mockk}")
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
            }
        }
        val jvmMain by getting { }
        val jvmTest by getting {
            dependencies {
                implementation("com.squareup.sqldelight:sqlite-driver:${Versions.sqlDelight}")
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
            }
        }
//        val jsTest by getting
//        val nativeTest by getting
    }
}

sqldelight {
    database("Database") {
        packageName = "net.folivo.trixnity.client.store.sqldelight.db"
    }
}