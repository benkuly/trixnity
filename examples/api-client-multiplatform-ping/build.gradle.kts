plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
        withJava()
    }
//    js {
//        nodejs { }
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
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation(project(":trixnity-client-api"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}-native-mt")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:${Versions.ktor}")
            }
        }
//        val jsMain by getting {
//            dependencies {
//                implementation("io.ktor:ktor-client-js:${Versions.ktor}")
//            }
//        }
//        val nativeMain by getting {
//            dependencies {
//                implementation("io.ktor:ktor-client-curl:${Versions.ktor}")
//            }
//        }
    }
}