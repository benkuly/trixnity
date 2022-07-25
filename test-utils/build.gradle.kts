plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
    }
    val jvmTarget = addDefaultJvmTargetWhenEnabled()
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir)
    val nativeTargets = addDefaultNativeTargetsWhenEnabled()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-core"))
                api(project(":trixnity-clientserverapi:trixnity-clientserverapi-model"))

                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")

                api("io.ktor:ktor-client-mock:${Versions.ktor}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}")
                implementation("io.ktor:ktor-resources:${Versions.ktor}")

                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTarget?.testSourceSet(this) {
            dependencies {
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}