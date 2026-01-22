plugins {
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    addJvmTarget()
    addJsTarget(rootDir)
    addNativeTargets()
}