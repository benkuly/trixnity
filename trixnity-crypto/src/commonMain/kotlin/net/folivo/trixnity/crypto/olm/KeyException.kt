package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.core.model.UserId

sealed class KeyException(message: String) : Exception(message) {
    class KeyNotFoundException(message: String) : KeyException(message)
    class KeyVerificationFailedException(message: String) : KeyException(message)
    class CouldNotReachRemoteServersException(servers: Set<String>) :
        KeyException("could not reach the following remote servers to retrieve keys: ${servers.joinToString()}")

    data class OneTimeKeyNotFoundException(val user: UserId, val device: String) :
        KeyException("one time key device could not be found for device $device of $user")
}