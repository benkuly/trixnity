package net.folivo.trixnity.client.key

sealed class DeviceTrustLevel {

    /**
     * The key is valid or verified.
     */
    data class Valid(val verified: Boolean) : DeviceTrustLevel()

    /**
     * The key is cross signed. The cross signing key could be verified and so this key is.
     */
    data class CrossSigned(val verified: Boolean) : DeviceTrustLevel()

    /**
     * There is a master key, but the key has not been cross signed yet.
     */
    object NotCrossSigned : DeviceTrustLevel()

    /**
     * The device key or a key, that signed this device key is blocked.
     */
    object Blocked : DeviceTrustLevel()
}
