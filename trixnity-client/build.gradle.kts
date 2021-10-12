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
            systemProperty("java.library.path", olm.build.canonicalPath)
            systemProperty("jna.library.path", olm.build.canonicalPath)
            dependsOn(":buildOlm")
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
                api(project(":trixnity-client-api"))
                implementation(project(":trixnity-olm"))
                implementation("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
                implementation("io.arrow-kt:arrow-fx-coroutines:${Versions.arrow}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
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
                implementation("io.kotest:kotest-framework-datatest:${Versions.kotest}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
            }
        }
//        val jsTest by getting
//        val nativeTest by getting
    }
}
