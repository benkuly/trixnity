plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
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
                implementation(project(":trixnity-client-sqldelight"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-java:${Versions.ktor}")
                implementation("com.squareup.sqldelight:sqlite-driver:${Versions.sqlDelight}")
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

//tasks {
//    create<JavaExec>("runJvm") {
//        group = "run"
//        classpath = objects.fileCollection().from(
//            named("compileKotlinJvm"),
//            named("compileJava"),
//            configurations.named("jvmRuntimeClasspath")
//        )
//        mainClass.set("net.folivo.trixnity.examples.multiplatform.JvmAppKt")
//        systemProperty("java.library.path", olm.build.canonicalPath)
//        systemProperty("jna.library.path", olm.build.canonicalPath)
//        dependsOn("build")
//        dependsOn(":buildOlm")
//    }
//}