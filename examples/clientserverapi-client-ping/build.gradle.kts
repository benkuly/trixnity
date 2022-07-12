import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(Versions.kotlinJvmTarget.majorVersion))
    }
    val jvmTarget = addDefaultJvmTargetWhenEnabled()
    val jsTarget = addDefaultJsTargetWhenEnabled(rootDir)
    val nativeTargets = setOfNotNull(
        addNativeTargetWhenEnabled(KonanTarget.LINUX_X64) { linuxX64() },
        addNativeTargetWhenEnabled(KonanTarget.MINGW_X64) { mingwX64() },
    )

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":trixnity-clientserverapi:trixnity-clientserverapi-client"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDatetime}")
            }
        }
        jvmTarget?.mainSourceSet(this) {
            dependencies {
                implementation("io.ktor:ktor-client-java:${Versions.ktor}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
            }
        }
        jsTarget?.mainSourceSet(this) {
            dependencies {
                implementation("io.ktor:ktor-client-js:${Versions.ktor}")
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("io.ktor:ktor-client-curl:${Versions.ktor}")
            }
        }
        nativeTargets.forEach {
            it.mainSourceSet(this).dependsOn(nativeMain)
        }
    }
}