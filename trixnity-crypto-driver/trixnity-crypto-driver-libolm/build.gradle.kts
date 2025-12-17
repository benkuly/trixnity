plugins {
    kotlin("multiplatform")
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeTargets()

    sourceSets {
        val commonMain by getting
        val commonTest by getting

        commonMain.dependencies {
            implementation(projects.trixnityCryptoDriver)
            implementation(projects.trixnityLibolm)
            implementation(projects.trixnityUtils)
        }

        commonTest.dependencies {
            implementation(projects.trixnityCryptoDriver.driverTest)
        }
    }
}