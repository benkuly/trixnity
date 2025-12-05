import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

private val KotlinMultiplatformExtension.nativeSourceSets: Set<KotlinSourceSet>
    get() = targets.matching { it is KotlinNativeTarget }
        .flatMap { it.compilations }
        .flatMap { it.allKotlinSourceSets }
        .toSet()

private val KotlinMultiplatformExtension.nonNativeSourceSets: Set<KotlinSourceSet>
    get() = targets.matching { it !is KotlinNativeTarget }
        .flatMap { it.compilations }
        .flatMap { it.allKotlinSourceSets }
        .toSet()

private val KotlinMultiplatformExtension.nativeExclusiveSourceSets: Set<KotlinSourceSet>
    get() = nativeSourceSets - nonNativeSourceSets

val KotlinSourceSet.isNativeOnly: Boolean
    get() = this in project.extensions.getByType<KotlinMultiplatformExtension>().nativeExclusiveSourceSets