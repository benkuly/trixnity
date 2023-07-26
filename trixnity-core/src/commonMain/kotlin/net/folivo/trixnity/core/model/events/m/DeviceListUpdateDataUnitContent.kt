package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralDataUnitContent
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.7/server-server-api/#device-management">matrix spec</a>
 */
@Serializable
data class DeviceListUpdateDataUnitContent(
    @SerialName("deleted")
    val deleted: Boolean? = null,
    @SerialName("device_display_name")
    val deviceDisplayName: String? = null,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("keys")
    val keys: SignedDeviceKeys? = null,
    @SerialName("prev_id")
    val previousStreamIds: List<Long>? = null,
    @SerialName("stream_id")
    val streamId: Long,
    @SerialName("user_id")
    val userId: UserId,
) : EphemeralDataUnitContent