plugins {
    id("com.android.library")
    kotlin("multiplatform")
    trixnity.publish
}

kotlin {
    jvmToolchain()
    applyDefaultHierarchyTemplate()

    addJsTarget(rootDir)
    val desktopTargets = addNativeDesktopTargets()

    addJvmTarget()
    addAndroidTarget()
    addNativeAppleTargets()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
        val commonMain by getting
        val directMain by creating {
            dependsOn(commonMain)
        }
        val logbackMain by creating {
            dependsOn(commonMain)
        }
        val jvmMain by getting {
            dependsOn(logbackMain)
        }
        val androidMain by getting {
            dependsOn(logbackMain)
        }
        val nativeDesktopMain by creating {
            dependsOn(directMain)
        }
        val jsMain by getting {
            dependsOn(directMain)
        }

        desktopTargets.forEach {
            getByName("${it.targetName}Main") {
                dependsOn(nativeDesktopMain)
            }
        }

        commonMain.dependencies {
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
            api(libs.kotest.assertions.core)
            api(libs.oshai.logging)
        }

        logbackMain.dependencies {
            implementation(libs.slf4j.api)
            implementation(libs.logback.classic)
        }

        jvmMain.dependencies {
            api(kotlin("test-junit5"))
        }

        androidMain.dependencies {
            api(kotlin("test-junit"))
        }
    }
}

android {
    namespace = "net.folivo.trixnity.test.utils"
    compileSdk = libs.versions.androidTargetSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
        }
    }
}