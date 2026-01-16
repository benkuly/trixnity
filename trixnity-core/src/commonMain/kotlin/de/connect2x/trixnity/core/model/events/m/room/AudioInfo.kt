package de.connect2x.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AudioInfo(
    @SerialName("duration")
    val duration: Long? = null,
    @SerialName("mimetype")
    override val mimeType: String? = null,
    @SerialName("size")
    override val size: Long? = null,
) : FileBasedInfo