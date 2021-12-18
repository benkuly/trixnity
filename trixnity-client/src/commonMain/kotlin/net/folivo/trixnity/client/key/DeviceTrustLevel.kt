package net.folivo.trixnity.client.key

sealed interface DeviceTrustLevel {

    /**
     * The key is verified e.g. via cross signing.
     */
    object Verified : DeviceTrustLevel

    /**
     * The key or the cross signing key is not verified.
     */
    object NotVerified : DeviceTrustLevel

    /**
     * There is a master key, but the key has not been cross signed yet.
     */
    object NotCrossSigned : DeviceTrustLevel

    /**
     * The device key or a key, that signed this device key is blocked.
     */
    object Blocked : DeviceTrustLevel

    /**
     * The trust level could not be calculated.
     */
    data class Invalid(val reason: String) : DeviceTrustLevel
}
