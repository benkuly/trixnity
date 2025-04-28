package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.ExperimentalTrixnityApi
import net.folivo.trixnity.core.model.UserId

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
    @ExperimentalTrixnityApi
    @SerialName("dehydrated")
    val dehydrated: Boolean? = null,
)

typealias SignedDeviceKeys = Signed<DeviceKeys, UserId>