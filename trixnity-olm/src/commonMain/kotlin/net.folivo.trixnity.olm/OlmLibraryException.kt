package net.folivo.trixnity.olm

class OlmLibraryException(
    message: String? = null,
    cause: Throwable? = null
) : IllegalStateException(message, cause)