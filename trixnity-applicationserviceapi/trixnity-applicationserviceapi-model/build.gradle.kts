plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = Versions.kotlinJvmTarget.toString()
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
        withJava()
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
        nodejs()
        binaries.executable()
    }

    linuxX64()
    mingwX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-core"))

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

                implementation("io.ktor:ktor-http:${Versions.ktor}")
                implementation("io.ktor:ktor-resources:${Versions.ktor}")
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting
        val jsTest by getting
        val nativeTest by creating {
            dependsOn(commonTest)
        }
        val linuxX64Test by getting {
            dependsOn(nativeTest)
        }
        val mingwX64Test by getting {
            dependsOn(nativeTest)
        }
    }
}