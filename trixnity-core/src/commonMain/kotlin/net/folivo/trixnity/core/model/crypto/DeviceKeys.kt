package net.folivo.trixnity.core.model.crypto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId

@Serializable
data class DeviceKeys(
    @SerialName("user_id")
    val userId: MatrixId.UserId,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("algorithms")
    val algorithms: Set<EncryptionAlgorithm>,
    @SerialName("keys")
    val keys: Keys,
    @SerialName("unsigned")
    val unsigned: UnsignedDeviceInfo? = null
) {
    @Serializable
    data class UnsignedDeviceInfo(
        @SerialName("device_display_name")
        val deviceDisplayName: String
    )
}