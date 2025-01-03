plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotest)
    trixnity.general
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    // does not use addNativeTargets() because some ar not supported yet
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }

        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                implementation(libs.oshai.logging)

                api(libs.androidx.room.runtime)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.trixnityClient.clientRepositoryTest)

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
        .filter {
            it.name != "ksp"
                    && it.name.startsWith("ksp")
                    && it.name.contains("Common").not()
                    && it.name.contains("Test").not()
        }
        .forEach {
            add(it.name, libs.androidx.room.compiler)
        }
}