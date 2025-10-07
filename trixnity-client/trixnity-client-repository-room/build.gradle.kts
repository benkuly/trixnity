plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinxKover)
    alias(libs.plugins.room)
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    // does not use addNativeTargets() because some ar not supported yet
    addNativeAppleTargets()
    linuxX64()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
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
                implementation(projects.trixnityTestUtils)
                implementation(projects.trixnityClient.clientRepositoryTest)

                implementation(libs.androidx.sqlite.bundled)

                implementation(libs.kotest.assertions.core)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.logback.classic)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
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