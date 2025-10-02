package net.folivo.trixnity.vodozemac

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * A simple runtime loader for shared libraries embedded as JAR resources. Since the JVM does not
 * support linking from a block of memory, but rather a file in the filesystem of the host, we
 * extract the required natives from within the JAR and place it into a temporary directory in the
 * user's application data directory.
 */
internal object NativeLoader {
    private const val PLATFORM_WINDOWS: String = "win32"
    private const val PLATFORM_LINUX: String = "linux"
    private const val PLATFORM_MACOS: String = "darwin"

    // If you change this, make sure to update the same code in build.gradle.kts too
    private val architecture: String =
        when (System.getProperty("os.arch")) {
            "i386",
            "i486",
            "i586",
            "i686",
            "x86",
            "x32" -> "x86"
            "x64",
            "amd64",
            "x86_64",
            "x86-64" -> "x86-64"
            "aarch64",
            "arm64" -> "arm64"
            "arm",
            "armv7",
            "aarch32" -> "arm32"
            else -> throw IllegalStateException("Could not determine host architecture")
        }
    private val os: String =
        System.getProperty("os.name").let {
            when {
                it.contains("windows", true) -> PLATFORM_WINDOWS
                it.contains("linux", true) -> PLATFORM_LINUX
                it.contains("osx", true) || it.contains("mac", true) -> PLATFORM_MACOS
                else -> throw IllegalStateException("Unsupported target platform")
            }
        }
    private val libPrefix: String = if (os == PLATFORM_WINDOWS) "" else "lib"

    private val libExtension: String =
        when (os) {
            PLATFORM_WINDOWS -> "dll"
            PLATFORM_MACOS -> "dylib"
            else -> "so"
        }

    private val isLoaded: AtomicBoolean = AtomicBoolean(false)

    @Suppress("UnsafeDynamicallyLoadedCode")
    private fun unpackAndLoad(name: String, ext: String = libExtension) {
        val filePath = Path("$os-$architecture") / "$libPrefix$name.$ext"
        val targetDirectory = Path(System.getProperty("user.home")) / ".libvodozemac"
        val targetPath = targetDirectory / filePath
        Files.createDirectories(targetPath.parent)
        this::class.java.getResourceAsStream("/${filePath.joinToString("/")}").use {
            Files.copy(
                requireNotNull(it) { "Could not read JAR resource" },
                targetPath,
                StandardCopyOption.REPLACE_EXISTING)
        }
        System.load(targetPath.pathString)
    }

    fun ensureLoaded() {
        if (!isLoaded.compareAndSet(false, true)) return
        try {
            unpackAndLoad("vodozemac")
        } catch (error: Throwable) { // Catch any type of error we might encounter and rethrow
            throw NativeLoaderException(error)
        }
    }
}

// This type allows the API consumer to catch the error on the JVM if required
class NativeLoaderException(error: Throwable) : RuntimeException("Could not load natives", error)
