plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    // TODO enable native targets as soon as it is more stable
    // does not use addNativeTargets() because mingw is not supported yet
    //addNativeAppleTargets()
    //linuxX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }

        commonMain {
            dependencies {
                implementation(project(":trixnity-client"))

                implementation(libs.oshai.logging)

                implementation(libs.androidx.room.runtime)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":trixnity-client:client-repository-test"))

                implementation(libs.androidx.sqlite.bundled)

                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.common)
                implementation(libs.kotest.framework.engine)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotest.runner.junit5)
                implementation(libs.logback.classic)
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