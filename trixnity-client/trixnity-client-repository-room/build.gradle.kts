plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    addJvmTarget()
    // does not use addNativeTargets() because some ar not supported yet
    addNativeAppleTargets()
    linuxX64()

    sourceSets {
        all {
            compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            languageSettings.optIn("kotlin.RequiresOptIn")
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                implementation(sharedLibs.lognity.api)

                api(libs.androidx.room.runtime)
            }
        }
        commonTest {
            dependencies {
                implementation(projects.trixnityTestUtils)
                implementation(projects.trixnityClient.clientRepositoryTest)

                implementation(libs.androidx.sqlite.bundled)

                implementation(sharedLibs.kotest.assertions.core)
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