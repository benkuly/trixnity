plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
}

kotlin {
    addJvmTarget()
    addJsTarget(rootDir)
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