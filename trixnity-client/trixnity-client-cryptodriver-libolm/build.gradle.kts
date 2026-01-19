plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
}

kotlin {
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