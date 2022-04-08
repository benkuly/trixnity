package net.folivo.trixnity.olm

fun <T> rethrow(block: () -> T): T = try {
    block()
} catch (error: Throwable) {
    throw OlmLibraryException(error.message?.substringAfter("OLM."), error)
}