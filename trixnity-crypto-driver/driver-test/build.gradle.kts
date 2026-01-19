plugins {
    kotlin("multiplatform")
}

kotlin {
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeTargets()

    sourceSets {
        val commonMain by getting

        commonMain.dependencies {
            api(projects.trixnityCryptoDriver)
            api(projects.trixnityTestUtils)
        }
    }
}