package net.folivo.trixnity.client.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.*
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import net.folivo.trixnity.crypto.key.UserTrustLevel

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("level")
@Serializable
sealed interface KeySignatureTrustLevel {

    /**
     * The key is valid or verified.
     * This level will never be set on master keys.
     */
    @Serializable
    @SerialName("valid")
    data class Valid(val verified: Boolean) : KeySignatureTrustLevel

    /**
     * The key is cross signed. The cross signing key could be verified and so this key is.
     * When set on master key, this means, that this key signs all devices.
     */
    @Serializable
    @SerialName("cross_signed")
    data class CrossSigned(val verified: Boolean) : KeySignatureTrustLevel

    /**
     * There is a master key, but the key has not been cross signed yet.
     * This level will never be set on master keys.
     */
    @Serializable
    @SerialName("not_cross_signed")
    data object NotCrossSigned : KeySignatureTrustLevel

    /**
     * Not all keys are signed by the master key.
     * This level gets only set on master keys.
     */
    @Serializable
    @SerialName("not_all_device_keys_cross_signed")
    data class NotAllDeviceKeysCrossSigned(val verified: Boolean) : KeySignatureTrustLevel

    /**
     * The device key or a key, that signed this device key is blocked.
     */
    @Serializable
    @SerialName("blocked")
    data object Blocked : KeySignatureTrustLevel

    /**
     * The trust level could not be calculated.
     */
    @Serializable
    @SerialName("invalid")
    data class Invalid(val reason: String) : KeySignatureTrustLevel
}

val KeySignatureTrustLevel.isVerified: Boolean
    get() = this is KeySignatureTrustLevel.CrossSigned && this.verified
            || this is KeySignatureTrustLevel.Valid && this.verified

fun KeySignatureTrustLevel?.toDeviceTrustLevel(): DeviceTrustLevel =
    when (this) {
        is Valid -> DeviceTrustLevel.Valid(verified)
        is CrossSigned -> DeviceTrustLevel.CrossSigned(verified)
        is NotCrossSigned -> DeviceTrustLevel.NotCrossSigned
        is Blocked -> DeviceTrustLevel.Blocked
        is Invalid -> DeviceTrustLevel.Invalid(reason)
        is NotAllDeviceKeysCrossSigned -> DeviceTrustLevel.Invalid("could not determine DeviceTrustLevel from $this")
        null -> DeviceTrustLevel.Unknown
    }

fun KeySignatureTrustLevel?.toUserTrustLevel(): UserTrustLevel =
    when (this) {
        is Valid -> UserTrustLevel.CrossSigned(verified)
        is CrossSigned -> UserTrustLevel.CrossSigned(verified)
        is NotAllDeviceKeysCrossSigned -> UserTrustLevel.NotAllDevicesCrossSigned(verified)
        is Blocked -> UserTrustLevel.Blocked
        is Invalid -> UserTrustLevel.Invalid(reason)
        is NotCrossSigned -> UserTrustLevel.Invalid("could not determine UserTrustLevel from $this")
        null -> UserTrustLevel.Unknown
    }