package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.model.UserId

@Serializable
data class DeviceKeys(
    @SerialName("user_id")
    val userId: UserId,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("algorithms")
    val algorithms: Set<EncryptionAlgorithm>,
    @SerialName("keys")
    val keys: Keys,
    @MSC3814
    @SerialName("dehydrated")
    val dehydrated: Boolean? = null,
)

typealias SignedDeviceKeys = Signed<DeviceKeys, UserId>