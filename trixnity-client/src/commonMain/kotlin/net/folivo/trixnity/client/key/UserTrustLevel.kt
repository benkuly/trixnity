package net.folivo.trixnity.client.key

sealed interface UserTrustLevel {

    /**
     * The user has cross signing enabled and all devices are cross signed.
     */
    data class CrossSigned(val verified: Boolean) : UserTrustLevel

    /**
     * The user has cross signing enabled, but not all devices are cross signed.
     */
    data class NotAllDevicesCrossSigned(val verified: Boolean) : UserTrustLevel

    /**
     * The users master key or a key, that signed this device key is blocked.
     */
    object Blocked : UserTrustLevel

    /**
     * The trust level could not be calculated.
     */
    data class Invalid(val reason: String) : UserTrustLevel
}
