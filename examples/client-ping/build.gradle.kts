plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
    }
    val jvmTarget = addDefaultJvmTargetWhenEnabled()
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
            }
        }
        jvmTarget?.mainSourceSet(sourceSets) {
            dependencies {
                implementation(project(":trixnity-client:trixnity-client-store-exposed"))
                implementation("io.ktor:ktor-client-java:${Versions.ktor}")
                implementation("com.h2database:h2:${Versions.h2}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
        jsTarget?.mainSourceSet(sourceSets) {
            dependencies {
                implementation("io.ktor:ktor-client-js:${Versions.ktor}")
            }
        }
    }
}