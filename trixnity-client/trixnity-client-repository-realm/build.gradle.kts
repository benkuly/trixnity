plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.realm)
    trixnity.general
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addNativeAppleTargets() // see https://github.com/realm/realm-kotlin/issues/617

    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
        commonMain {
            dependencies {
                implementation(projects.trixnityClient)

                implementation(libs.oshai.logging)

                api(libs.realm.base)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.trixnityClient.clientRepositoryTest)
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