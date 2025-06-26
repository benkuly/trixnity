import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val kotlinJvmTarget = JvmTarget.JVM_11

fun KotlinMultiplatformExtension.jvmToolchain() = jvmToolchain(11)