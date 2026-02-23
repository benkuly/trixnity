package de.connect2x.trixnity.libolm

inline fun <T> rethrow(crossinline block: () -> T): T = try {
    block()
} catch (error: Throwable) {
    throw OlmLibraryException(error.message?.substringAfter("OLM."), error)
}