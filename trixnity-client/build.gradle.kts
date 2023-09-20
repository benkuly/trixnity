plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("io.kotest.multiplatform")
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addJsTarget(rootDir, testEnabled = false)
    addNativeTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                api(project(":trixnity-clientserverapi:trixnity-clientserverapi-client"))
                api(project(":trixnity-crypto"))

                api("io.insert-koin:koin-core:${Versions.koin}")

                api("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")

                implementation("io.arrow-kt:arrow-resilience:${Versions.arrow}")

                implementation("com.benasher44:uuid:${Versions.uuid}")

                implementation("io.github.oshai:kotlin-logging:${Versions.kotlinLogging}")

                implementation("com.soywiz.korlibs.korim:korim:${Versions.korlibs}")
                implementation("com.soywiz.korlibs.krypto:krypto:${Versions.korlibs}")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":test-utils"))

                implementation("io.ktor:ktor-client-mock:${Versions.ktor}")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinxCoroutines}")
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-datatest:${Versions.kotest}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
    }
}
