package net.folivo.trixnity.client.api.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.Signed

@Serializable
data class SetDeviceKeysRequest(
    @SerialName("device_keys")
    val deviceKeys: Signed<DeviceKeys, UserId>?,
    @SerialName("one_time_keys")
    val oneTimeKeys: Keys?
)