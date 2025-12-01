plugins {
    kotlin("multiplatform")
    trixnity.publish
}

kotlin {
    jvmToolchain()
    addJvmTarget()
    addNativeTargets()
    addJsTarget(rootDir)

    sourceSets {
        val commonMain by getting

        commonMain.dependencies {
            implementation(projects.trixnityClient)
            implementation(projects.trixnityCryptoDriver.trixnityCryptoDriverLibolm)
        }
    }
}