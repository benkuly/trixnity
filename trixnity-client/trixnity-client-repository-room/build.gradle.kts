plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
}

android {
    namespace = "net.folivo.trixnity.client.store.repository.room"
    compileSdk = libs.versions.androidTargetSdk.get().toInt()
    buildToolsVersion = libs.versions.androidBuildTools.get()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = kotlinJvmTarget
        targetCompatibility = kotlinJvmTarget
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

                implementation(libs.oshai.logging)

                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room.ktx)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.androidx.room.testing)
                implementation(libs.androidx.test.core)
                implementation(libs.logback.classic)
                implementation(libs.benasher44.uuid)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.common)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.runner.junit5)
                implementation(libs.robolectric)
            }
        }
    }
}

dependencies {
    configurations
        .filter { it.name.startsWith("ksp") }
        .forEach {
            add(it.name, libs.androidx.room.compiler)
        }
}