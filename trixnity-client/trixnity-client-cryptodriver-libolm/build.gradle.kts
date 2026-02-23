plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addNativeTargets()
    addWebTarget(rootDir)

    sourceSets {
        val commonMain by getting

        commonMain.dependencies {
            implementation(projects.trixnityClient)
            implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverLibolm)
        }
    }
}