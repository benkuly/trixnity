package net.folivo.trixnity.clientserverapi.model.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

@Serializable
data class SetDeviceKeysRequest(
    @SerialName("device_keys")
    val deviceKeys: SignedDeviceKeys?,
    @SerialName("one_time_keys")
    val oneTimeKeys: Keys?
)