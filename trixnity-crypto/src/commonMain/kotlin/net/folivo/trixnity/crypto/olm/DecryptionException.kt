package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.olm.OlmLibraryException

sealed class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    object SenderDidNotSendMegolmKeysToUs :
        DecryptionException("The sender did not send us the key to decrypt the message.")

    object SenderDidNotEncryptForThisDeviceException :
        DecryptionException("the sender did not encrypt the message for this device")

    object CouldNotDecrypt :
        DecryptionException("could not decrypt with any olm session, but try to create new session")

    object PreventToManySessions :
        DecryptionException("the last 5 created sessions with that sender are less then an hour old, so we do not create one again")

    class ValidationFailed(message: String) :
        DecryptionException("The validation failed. This could be due to a man-in-the-middle attack. Reason: $message")

    class SessionException(cause: OlmLibraryException) :
        DecryptionException("There was a problem with the session: ${cause.message}", cause)

    class OtherException(cause: Throwable) :
        DecryptionException("There was a problem while decrypting: ${cause.message}", cause)
}