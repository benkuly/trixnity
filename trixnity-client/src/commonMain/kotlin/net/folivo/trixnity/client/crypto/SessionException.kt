package net.folivo.trixnity.client.crypto

sealed class SessionException(message: String) : Exception(message) {
    object SenderDidNotEncryptForThisDeviceException :
        SessionException("the sender did not encrypt the message for this device")

    object CouldNotDecrypt :
        SessionException("could not decrypt with any olm session, but try to create new session")

    object PreventToManySessions :
        SessionException("the last 5 created sessions with that sender are less then an hour old, so we do not create one again")

    object ValidationFailed :
        DecryptionException("The validation failed. This could be due to a man-in-the-middle attack.")
}