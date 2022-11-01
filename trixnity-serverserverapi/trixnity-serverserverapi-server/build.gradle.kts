plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.kodein.mock.mockmp")
}

mockmp {
    usesHelper = true
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    val jvmTarget = addDefaultJvmTargetWhenEnabled(useJUnitPlatform = false)
    val linuxX64Target =
        addNativeTargetWhenEnabled(org.jetbrains.kotlin.konan.target.KonanTarget.LINUX_X64) { linuxX64() }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                api(project(":trixnity-api-server"))
                api(project(":trixnity-serverserverapi:trixnity-serverserverapi-model"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")

                implementation("io.ktor:ktor-server-auth:${Versions.ktor}")
                implementation("io.ktor:ktor-server-double-receive:${Versions.ktor}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")

                implementation("io.ktor:ktor-server-test-host:${Versions.ktor}")
                implementation("io.ktor:ktor-server-resources:${Versions.ktor}")
                implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktor}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}")

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