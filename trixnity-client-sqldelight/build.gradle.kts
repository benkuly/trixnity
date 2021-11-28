plugins {
    kotlin("plugin.serialization")
    kotlin("multiplatform")
    id("com.squareup.sqldelight")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(JavaVersion.VERSION_11.ordinal))
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
        }
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
                implementation("io.ktor:ktor-client-java:${Versions.ktor}")
                implementation("io.ktor:ktor-client-logging:${Versions.ktor}")
                implementation("org.testcontainers:testcontainers:${Versions.testContainers}")
                implementation("org.testcontainers:junit-jupiter:${Versions.testContainers}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
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