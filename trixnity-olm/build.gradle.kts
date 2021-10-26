import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("plugin.serialization")
    id("com.android.library")
    kotlin("multiplatform")
}

android {
    compileSdk = 30
    defaultConfig {
        minSdk = 26
        targetSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main") {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    externalNativeBuild {
        cmake {
            path = file(olm.cMakeLists)
        }
    }
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
    android {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
        }
        publishLibraryVariants("release")
    }
//    js(IR) { // TODO enable
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
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
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
        val androidMain by getting {
            dependsOn(olmLibraryMain)
            kotlin.srcDirs("src/jvmMain/kotlin")
            dependencies {
                api("net.java.dev.jna:jna:${Versions.jna}@aar")
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
        val androidTest by getting {
            kotlin.srcDirs("src/jvmTest/kotlin")
            dependencies {
                implementation("androidx.test:runner:1.4.0")
            }
        }
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

tasks.withType<com.android.build.gradle.tasks.ExternalNativeCleanTask> {
    dependsOn(":extractOlm")
}

tasks.withType<com.android.build.gradle.tasks.ExternalNativeBuildTask> {
    dependsOn(":extractOlm")
}

project.afterEvaluate {
    val testTasks =
        project.getTasksByName("testReleaseUnitTest", false) + project.getTasksByName("testDebugUnitTest", false)
    testTasks.forEach {
        it.onlyIf { false }
    }
}