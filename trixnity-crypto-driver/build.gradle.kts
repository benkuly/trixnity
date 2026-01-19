plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
}

kotlin {
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeTargets()
}