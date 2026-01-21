import org.gradle.internal.jvm.Jvm

plugins {
    `kotlin-dsl`
}

// TODO: update this when kotlin supports Java 25
val javaVersion = minOf(Jvm.current().javaVersion?.ordinal ?: 24, 24)
kotlin.jvmToolchain(javaVersion)

dependencies {
    implementation(sharedLibs.plugins.kotlin.multiplatform.asLibrary())
    implementation(sharedLibs.plugins.android.library.asLibrary())
}

fun Provider<PluginDependency>.asLibrary(): Provider<String>
    = map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }