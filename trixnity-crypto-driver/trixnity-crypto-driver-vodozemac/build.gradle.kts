plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addWebTarget(rootDir)
    addNativeTargets()

    sourceSets {
        val commonMain by getting
        val commonTest by getting

        commonMain.dependencies {
            implementation(projects.trixnityCryptoDriver)
            implementation(projects.trixnityVodozemac)
        }

        commonTest.dependencies {
            implementation(projects.trixnityCryptoDriver.driverTest)
        }
    }
}