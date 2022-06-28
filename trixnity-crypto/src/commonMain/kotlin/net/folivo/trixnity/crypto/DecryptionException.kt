package net.folivo.trixnity.crypto

import net.folivo.trixnity.olm.OlmLibraryException

sealed class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object SenderDidNotSendMegolmKeysToUs :
        DecryptionException("The sender did not send us the key to decrypt the message.")

    object ValidationFailed :
        DecryptionException("The validation failed. This could be due to a man-in-the-middle attack.")

    data class SessionException(override val cause: OlmLibraryException) :
        DecryptionException("There was a problem with the session: ${cause.message}", cause)

    data class OtherException(override val cause: Throwable) :
        DecryptionException("There was a problem while decrypting: ${cause.message}", cause)
}