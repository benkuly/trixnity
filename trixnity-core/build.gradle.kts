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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
                api("io.ktor:ktor-http:${Versions.ktor}")
                implementation("io.github.microutils:kotlin-logging:${Versions.kotlinLogging}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
            }
        }
        jvmTarget?.testSourceSet(this) {
            dependencies {
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}