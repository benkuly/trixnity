package net.folivo.trixnity.client.key

sealed interface DeviceTrustLevel {

    /**
     * The device key is valid, but not cross signed.
     */
    data class Valid(val verified: Boolean) : DeviceTrustLevel

    /**
     * The device key is cross signed.
     */
    data class CrossSigned(val verified: Boolean) : DeviceTrustLevel

    /**
     * There is a master key, but the device key has not been cross signed yet.
     */
    object NotCrossSigned : DeviceTrustLevel

    /**
     * The timeline event cannot be trusted.
     */
    object NotTrusted : DeviceTrustLevel

    /**
     * The device key or a key, that signed this device key is blocked.
     */
    object Blocked : DeviceTrustLevel

    /**
     * The trust level could not be calculated.
     */
    data class Invalid(val reason: String) : DeviceTrustLevel

    /**
     * There are no stored cross signing keys of this user yet.
     */
    object Unknown : DeviceTrustLevel
}
