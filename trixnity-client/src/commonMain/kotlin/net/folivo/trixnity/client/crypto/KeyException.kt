package net.folivo.trixnity.client.crypto

sealed class KeyException(message: String) : Exception(message) {
    class KeyNotFoundException(message: String) : KeyException(message)
    class KeyVerificationFailedException(message: String) : KeyException(message)
    class CouldNotReachRemoteServersException(servers: Set<String>) :
        KeyException("could not reach the following remote servers to retrieve keys: ${servers.joinToString()}")

    object OneTimeKeyNotFoundException : KeyException("one time key device could not be found")
    object SenderDidNotEncryptForThisDeviceException :
        KeyException("the sender did not encrypt the message for this device")

    object OutdatedKeysException : KeyException("there are some outdated keys. They must be updated first.")
}