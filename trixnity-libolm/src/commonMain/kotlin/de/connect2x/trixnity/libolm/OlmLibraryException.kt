package de.connect2x.trixnity.libolm

class OlmLibraryException(
    message: String? = null,
    cause: Throwable? = null
) : IllegalStateException(message, cause)