package net.folivo.trixnity.olm

class OlmLibraryException(message: String? = null, cause: Throwable? = null) : IllegalStateException(message, cause) {
    constructor(cause: Exception) : this(null, cause)
}