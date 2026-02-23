plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addWebTarget(rootDir)
    addNativeTargets()

    sourceSets {
        val commonMain by getting

        commonMain.dependencies {
            api(projects.trixnityCryptoDriver)
            api(projects.trixnityTestUtils)
        }
    }
}