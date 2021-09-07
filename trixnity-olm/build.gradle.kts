import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("plugin.serialization")
    kotlin("multiplatform")
    id("de.undercouch.download")
}

//repositories { TODO for android
//    maven {
//        url = URI("https://jitpack.io")
//        content {
//            includeGroupByRegex("org\\.matrix\\.gitlab\\.matrix-org")
//        }
//    }
//}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            systemProperty("java.library.path", olm.build.canonicalPath)
            systemProperty("jna.library.path", olm.build.canonicalPath)
            dependsOn(":buildOlm")
        }
        withJava()
    }
//    js(IR) { // FIXME enable on kotlin >= 1.5.30
//        browser {
//            testTask {
//                useKarma {
//                    useFirefoxHeadless()
//                    useConfigDirectory(rootDir.resolve("karma.config.d"))
//                    webpackConfig.configDirectory = rootDir.resolve("webpack.config.d")
//                }
//            }
//        }
//        binaries.executable()
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
        all {
            languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}-native-mt")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerializationJson}")
                implementation("io.ktor:ktor-utils:${Versions.ktor}")
            }
        }
        val olmLibraryMain = create("olmLibraryMain") {
            dependsOn(commonMain)
        }
        val jvmMain by getting {
            dependsOn(olmLibraryMain)
            dependencies {
                implementation("net.java.dev.jna:jna:${Versions.jna}")
            }
        }
        val nativeMain by getting {
            dependsOn(olmLibraryMain)
        }
//        val jsMain by getting {
//            dependencies {
//                implementation(npm("@matrix-org/olm", Versions.olm, generateExternals = false))
//            }
//        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
            }
        }
        val jvmTest by getting
//        val jsTest by getting
        val nativeTest by getting
    }
    targets.withType<KotlinNativeTarget> {
        compilations {
            "main" {
                cinterops {
                    create("libolm") {
                        tasks.named(interopProcessingTaskName) {
                            dependsOn(":buildOlm")
                        }
                        includeDirs(olm.include)
                    }
                }
                binaries.all {
                    linkerOpts(
                        "-rpath", olm.build.canonicalPath,
                        "-L${olm.build}", "-lolm"
                    )
                }
                defaultSourceSet {
                    kotlin.srcDir("src/nativeMain/kotlin")
                    resources.srcDir("src/nativeMain/resources")
                }
                dependencies {
                }
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xir-property-lazy-initialization"
    }
}