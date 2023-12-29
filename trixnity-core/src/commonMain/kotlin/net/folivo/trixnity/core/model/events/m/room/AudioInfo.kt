package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudioInfo(
    @SerialName("duration")
    val duration: Int? = null,
    @SerialName("mimetype")
    override val mimeType: String? = null,
    @SerialName("size")
    override val size: Int? = null,
) : FileBasedInfo