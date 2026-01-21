plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(sharedLibs.plugins.kotlin.multiplatform.asLibrary())
    implementation(sharedLibs.plugins.android.library.asLibrary())
}

fun Provider<PluginDependency>.asLibrary(): Provider<String>
    = map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }