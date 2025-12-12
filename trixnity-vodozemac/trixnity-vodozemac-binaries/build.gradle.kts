import java.util.Properties
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.properties.hasProperty
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.net.URI
import java.security.DigestOutputStream
import java.security.MessageDigest

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    trixnity.publish
}

registerLibvodozemac(
    VodozemacBinaries.Remote(
        baseUrl =
            "https://gitlab.com/api/v4/projects/trixnity%2Ftrixnity-vodozemac-binaries/packages/generic/libvodozemac",
        version = libs.versions.vodozemac.tag,
        hashes =
            Hashes(
                jvmJni = libs.versions.vodozemac.jvmJni,
                androidJni = libs.versions.vodozemac.androidJni,
                native = libs.versions.vodozemac.native,
                webNpm = libs.versions.vodozemac.webNpm)))

kotlin {
    jvmToolchain()
    addJvmTarget()
    addAndroidTarget()
    addNativeTargets()
    addNativeAppleTargets()
    addJsTarget(rootDir)

    applyDefaultHierarchyTemplate()
}

android {
    namespace = "net.folivo.trixnity.vodozemac.binaries"
    compileSdk = libs.versions.androidTargetSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

androidComponents {
    beforeVariants {
        if (it.buildType == "debug") {
            it.enable = false
        }
    }
}

data class Hashes(
    val jvmJni: Provider<String>,
    val androidJni: Provider<String>,
    val native: Provider<String>,
    val webNpm: Provider<String>,
)

sealed interface VodozemacBinaries {
    data class Remote(
        val baseUrl: String,
        val version: Provider<String>,
        val hashes: Hashes,
    ) : VodozemacBinaries

    data class Local(
        val directory: String,
    ) : VodozemacBinaries
}

data class Directories<T : OutputDirProvider>(
    val jvmJni: Provider<Directory>,
    val androidJni: TaskProvider<T>,
    val native: Provider<Directory>,
    val webNpm: Provider<String>,
)

fun registerLibvodozemacLocal(binaries: VodozemacBinaries.Local): Directories<*> {
    val downloadLibvodozemacJvmJniArchive by
    tasks.registering(CopyTar::class) {
        file = file("${binaries.directory}/libvodozemac-jvm-jni.tar.gz")
        outputDir = layout.buildDirectory.dir("binaries/local/jvm")
    }

    val downloadLibvodozemacAndroidJniArchive by
    tasks.registering(CopyTar::class) {
        file = file("${binaries.directory}/libvodozemac-android-jni.tar.gz")
        outputDir = layout.buildDirectory.dir("binaries/local/android")
    }

    val downloadLibvodozemacNativeArchive by
    tasks.registering(CopyTar::class) {
        file = file("${binaries.directory}/libvodozemac-native.tar.gz")
        outputDir = layout.buildDirectory.dir("binaries/local/native")
    }

    val downloadLibvodozemacWebArchive by
    tasks.registering(CopyTar::class) {
        file = file("${binaries.directory}/libvodozemac-web-npm.tar.gz")
        outputDir = layout.buildDirectory.dir("binaries/local/web")
    }

    tasks
        .named { it == "jsPackageJson" }
        .configureEach { dependsOn(downloadLibvodozemacWebArchive) }

    return Directories(
        jvmJni = downloadLibvodozemacJvmJniArchive.flatMap { it.outputDir },
        androidJni = downloadLibvodozemacAndroidJniArchive,
        native = downloadLibvodozemacNativeArchive.flatMap { it.outputDir },
        webNpm = layout.buildDirectory.dir("binaries/local/web").map { it.asFile.canonicalPath },
    )
}

fun registerLibvodozemacRemote(binaries: VodozemacBinaries.Remote): Directories<*> {
    val fullUrl = binaries.version.map { "${binaries.baseUrl}/$it" }

    val downloadLibvodozemacJvmJniArchive by
    tasks.registering(DownloadAndUnpack::class) {
        url = fullUrl.map { "$it/libvodozemac-jvm-jni.tar.gz" }
        sha256 = binaries.hashes.jvmJni
        outputDir = layout.buildDirectory
            .dir("binaries/remote/jvm")
            .flatMap { it.dir(binaries.hashes.jvmJni) }
    }

    val downloadLibvodozemacAndroidJniArchive by
    tasks.registering(DownloadAndUnpack::class) {
        url = fullUrl.map { "$it/libvodozemac-android-jni.tar.gz" }
        sha256 = binaries.hashes.androidJni
        outputDir = layout.buildDirectory
            .dir("binaries/remote/android")
            .flatMap { it.dir(binaries.hashes.androidJni) }
    }

    val downloadLibvodozemacNativeArchive by
    tasks.registering(DownloadAndUnpack::class) {
        url = fullUrl.map { "$it/libvodozemac-native.tar.gz" }
        sha256 = binaries.hashes.native
        outputDir = layout.buildDirectory
            .dir("binaries/remote/native")
            .flatMap { it.dir(binaries.hashes.native) }
    }

    return Directories(
        jvmJni = downloadLibvodozemacJvmJniArchive.flatMap { it.outputDir },
        androidJni = downloadLibvodozemacAndroidJniArchive,
        native = downloadLibvodozemacNativeArchive.flatMap { it.outputDir },
        webNpm = combine(fullUrl, binaries.hashes.webNpm) { url, hash ->
            "$url/libvodozemac-web-npm.tar.gz#$hash"
        }
    )
}

private fun registerLibvodozemac(remoteBinaries: VodozemacBinaries.Remote) {
    val localProperties =
        Properties().apply {
            val propertyFile = rootProject.file("local.properties")

            if (propertyFile.exists()) {
                load(propertyFile.inputStream())
            }
        }

    val binaries =
        if (localProperties.hasProperty("libvodozemac")) {
            VodozemacBinaries.Local(directory = localProperties.getProperty("libvodozemac"))
        } else remoteBinaries

    val directories: Directories<*> =
        when (binaries) {
            is VodozemacBinaries.Remote -> registerLibvodozemacRemote(binaries)
            is VodozemacBinaries.Local -> registerLibvodozemacLocal(binaries)
        }

    kotlin.sourceSets
        .named { it == "jsMain" }
        .configureEach { dependencies { implementation(npm("vodozemac", directories.webNpm.get())) } }

    val cinteropDefFile by
    tasks.registering(WriteDefFile::class) {
        nativeLibDir = directories.native
        defFile = layout.buildDirectory.file("binaries/libvodozemac.def")
        targets =
            provider {
                kotlin.targets.withType<KotlinNativeTarget>().map { it.konanTarget }
            }
    }

    kotlin.sourceSets
        .named { it == "jvmMain" }
        .configureEach { resources.srcDir(directories.jvmJni) }

    androidComponents.onVariants { variant ->
        val jniLibs = checkNotNull(variant.sources.jniLibs) { "jniLibs missing" }
        jniLibs.addGeneratedSourceDirectory(directories.androidJni, OutputDirProvider::outputDir)
    }

    kotlin.targets.withType<KotlinNativeTarget> {
        val main by
        compilations.getting {
            val libvodozemac by
            cinterops.creating { definitionFile = cinteropDefFile.flatMap { it.defFile } }
        }
    }

    if (binaries is VodozemacBinaries.Local) {
        tasks
            .named { it.startsWith("publishJsPublication") }
            .configureEach {
                doFirst {
                    throw GradleException(
                        """
                    Cannot publish JS when using local libvodozemac (-Plibvodozemac=<DIRECTORY>).
                    This is a limitation of Kotlin/Js which would require downstream users to also have a local version of libvodozemac.
                    Currently Kotlin/JS resources are not properly bundled to be retrievable via @JsModule and alike...
                    """
                            .trimIndent()
                    )
                }
            }
    }
}

abstract class WriteDefFile @Inject constructor(
) : DefaultTask() {

    @get:Input
    abstract val targets: ListProperty<KonanTarget>

    @get:InputDirectory
    abstract val nativeLibDir: DirectoryProperty

    @get:OutputFile
    abstract val defFile: RegularFileProperty

    @TaskAction
    fun run() {
        val input = nativeLibDir.get()
        val output = defFile.get().asFile

        val targetNames = targets.get().map { it.name }

        val libPaths = targetNames.joinToString("\n") { target ->
            "libraryPaths.$target = $input/$target"
        }

        val libNames = targetNames.joinToString("\n") { target ->
            "staticLibraries.$target = libvodozemac.a"
        }

        output.parentFile.mkdirs()
        output.writeText(
            """
            package = net.folivo.trixnity.vodozemac

            $libPaths
            $libNames

            linkerOpts.mingw = -lkernel32 -luser32 -lntdll
        """.trimIndent()
        )
    }
}


abstract class OutputDirProvider : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

abstract class DownloadAndUnpack @Inject constructor(
    objects: ObjectFactory,
    private val layout: ProjectLayout,
    private val fileSystemOperations: FileSystemOperations,
) : OutputDirProvider() {

    @get:Input
    val url: Property<String> = objects.property<String>()

    @get:Input
    val sha256: Property<String> = objects.property<String>()


    @TaskAction
    fun run() {
        val url = url.get()
        val outputDirectory = outputDir.get()
        val tmpFile = layout.buildDirectory
            .file("tmp/archives/${url.split("/").last()}")
            .get()
            .asFile
        val sha256 = sha256.get()

        tmpFile.parentFile.mkdirs()

        val sha256Instance = MessageDigest.getInstance("SHA-256")

        URI(url).toURL().openStream().use { input ->
            DigestOutputStream(tmpFile.outputStream(), sha256Instance).use { output ->
                input.copyTo(output)
            }
        }

        val actualHash = sha256Instance.digest()
            .joinToString("") { "%02x".format(it) }

        if (!sha256.equals(actualHash, ignoreCase = true)) {
            throw GradleException(
                """
                ❌ SHA-256 checksum verification failed!
                ▶ File: file://${tmpFile.absoluteFile.path}
                ▶ Expected: '$sha256'
                ▶ Actual  : '$actualHash'
                """.trimIndent()
            )
        }

        fileSystemOperations.copy {
            from(project.tarTree(tmpFile))
            into(outputDirectory)
        }
    }
}

abstract class CopyTar @Inject constructor(
    objects: ObjectFactory,
    private val fileSystemOperations: FileSystemOperations,
) : OutputDirProvider() {

    @get:InputFile
    val file: RegularFileProperty = objects.fileProperty()

    @TaskAction
    fun run() {
        fileSystemOperations.copy {
            from(project.tarTree(file))
            into(outputDir)
        }
    }
}

private fun <T: Any, U: Any, R: Any> combine(left: Provider<T>, right: Provider<U>, f: (T, U) -> R) : Provider<R>
    = left.zip(right, f)
