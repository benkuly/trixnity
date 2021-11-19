package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SasMethod {
    @SerialName("decimal")
    DECIMAL,

    @SerialName("emoji")
    EMOJI
}