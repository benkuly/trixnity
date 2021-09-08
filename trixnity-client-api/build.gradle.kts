plugins {
    kotlin("plugin.serialization")
    kotlin("multiplatform")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        withJava()
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
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}-native-mt")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                api("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("io.ktor:ktor-client-serialization:${Versions.ktor}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
                api("org.kodein.log:kodein-log:${Versions.kodeinLog}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:${Versions.ktor}")
                implementation("io.kotest:kotest-assertions-json:${Versions.kotest}")
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
//        val jsTest by getting
//        val nativeTest by getting
    }
}