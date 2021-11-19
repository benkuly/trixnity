package net.folivo.trixnity.client.api.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.DeviceKeys
import net.folivo.trixnity.core.model.crypto.Keys
import net.folivo.trixnity.core.model.crypto.Signed

@Serializable
data class UploadKeysRequest(
    @SerialName("device_keys")
    val deviceKeys: Signed<DeviceKeys, UserId>?,
    @SerialName("one_time_keys")
    val oneTimeKeys: Keys?
)