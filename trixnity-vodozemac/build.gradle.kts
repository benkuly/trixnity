import com.android.build.gradle.tasks.factory.AndroidUnitTest
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    builtin(sharedLibs.plugins.android.library)
    builtin(sharedLibs.plugins.kotlin.multiplatform)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        group("common") {
            group("web") {
                withJs()

                // TODO: Enable Wasm !544
                // withWasmJs()
            }
            group("jni") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    addJvmTarget()
    addAndroidTarget()
    addJsTarget(rootDir)
    addNativeTargets()

    compilerOptions {
        freeCompilerArgs.add(
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        val webMain = named("webMain")
        configureEach {
            if (isNativeOnly) languageSettings.optIn("kotlin.native.SymbolNameIsInternal")
        }

        commonMain.dependencies {
            implementation(projects.trixnityVodozemac.trixnityVodozemacBinaries)
        }
        webMain.dependencies {
            implementation(project.dependencies.platform(sharedLibs.kotlin.wrappers.bom))
            implementation(libs.kotlin.wrappers.browser)
        }

        commonTest.dependencies {
            implementation(sharedLibs.kotlin.test)
            implementation(sharedLibs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "de.connect2x.trixnity.vodozemac"
    compileSdk = libs.versions.androidTargetSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

kotlin.targets.withType<KotlinNativeTarget> {
    compilerOptions { freeCompilerArgs.add("-opt-in=kotlin.native.SymbolNameIsInternal") }
    val main by
        compilations.getting {
            defaultSourceSet.languageSettings.optIn("kotlin.native.SymbolNameIsInternal")
        }
}

tasks.withType<Test> { outputs.cacheIf { false } }

tasks.withType<AndroidUnitTest> { enabled = false }
