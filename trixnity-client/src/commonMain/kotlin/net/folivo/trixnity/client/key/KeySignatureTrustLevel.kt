package net.folivo.trixnity.client.key

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("level")
@Serializable
sealed class KeySignatureTrustLevel {

    /**
     * The key is valid or verified.
     * This level will never be set on master keys.
     */
    @Serializable
    @SerialName("valid")
    data class Valid(val verified: Boolean) : KeySignatureTrustLevel()

    /**
     * The key is cross signed. The cross signing key could be verified and so this key is.
     * When set on master key, this means, that this key signs all devices.
     */
    @Serializable
    @SerialName("cross_signed")
    data class CrossSigned(val verified: Boolean) : KeySignatureTrustLevel()

    /**
     * There is a master key, but the key has not been cross signed yet.
     * This level will never be set on master keys.
     */
    @Serializable
    @SerialName("not_cross_signed")
    object NotCrossSigned : KeySignatureTrustLevel()

    /**
     * Not all keys are signed by the master key.
     * This level gets only set on master keys.
     */
    @Serializable
    @SerialName("not_all_device_keys_cross_signed")
    data class NotAllDeviceKeysCrossSigned(val verified: Boolean) : KeySignatureTrustLevel()

    /**
     * The device key or a key, that signed this device key is blocked.
     */
    @Serializable
    @SerialName("blocked")
    object Blocked : KeySignatureTrustLevel()

    /**
     * The trust level could not be calculated.
     */
    @Serializable
    @SerialName("invalid")
    data class Invalid(val reason: String) : KeySignatureTrustLevel()
}
