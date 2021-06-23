plugins {
    kotlin("plugin.serialization")
    kotlin("multiplatform")
}

kotlin {
    sourceSets {
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
//    js(IR) {
//        browser {
//            testTask {
//                useKarma {
//                    useFirefoxHeadless()
//                }
//            }
//        }
//    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api(project(":trixnity-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}-native-mt")
                implementation("co.touchlab:stately-concurrency:${Versions.stately}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                api("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("io.ktor:ktor-client-serialization:${Versions.ktor}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
                api("com.soywiz.korlibs.klogger:klogger:${Versions.klogger}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common")) // FIXME change to test in kotlin 1.5
                implementation(kotlin("test-annotations-common"))
                implementation("io.ktor:ktor-client-mock:${Versions.ktor}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))// FIXME remove in kotlin 1.5
            }
        }
//        val jsTest by getting {
//            dependencies {
//                implementation(kotlin("test-js"))// FIXME remove in kotlin 1.5
//            }
//        }
        val nativeTest by getting
    }
}