plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "net.folivo.trixnity.client.store.repository.room"
    compileSdk = Versions.androidTargetSdk
    buildToolsVersion = Versions.androidBuildTools
    defaultConfig {
        minSdk = Versions.androidMinSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = Versions.kotlinJvmTarget
        targetCompatibility = Versions.kotlinJvmTarget
    }
    buildTypes {
        release {
            isDefault = true
        }
    }
}

kotlin {
    addAndroidTarget()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }

        val androidMain by getting {
            dependencies {
                implementation(project(":trixnity-client"))

                implementation("io.github.oshai:kotlin-logging:${Versions.kotlinLogging}")

                implementation("androidx.room:room-ktx:${Versions.androidxRoom}")
                implementation("androidx.room:room-runtime:${Versions.androidxRoom}")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("androidx.room:room-testing:${Versions.androidxRoom}")
                implementation("androidx.test:core-ktx:${Versions.androidxTextKtx}")
                implementation("ch.qos.logback:logback-classic:${Versions.logback}")
                implementation("com.benasher44:uuid:${Versions.uuid}")
                implementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
                implementation("io.kotest:kotest-common:${Versions.kotest}")
                implementation("io.kotest:kotest-framework-engine:${Versions.kotest}")
                implementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
                implementation("org.robolectric:robolectric:${Versions.robolectric}")
            }
        }
    }
}

dependencies {
    configurations
        .filter { it.name.startsWith("ksp") }
        .forEach {
            add(it.name, "androidx.room:room-compiler:${Versions.androidxRoom}")
        }
}