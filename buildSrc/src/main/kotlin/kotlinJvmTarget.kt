import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

val kotlinJvmTarget = JavaVersion.VERSION_11

val JavaVersion.number: Int
    get() = JavaLanguageVersion.of(this.majorVersion).asInt()

fun KotlinMultiplatformExtension.jvmToolchain() = jvmToolchain(kotlinJvmTarget.number)