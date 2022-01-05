package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class KeyRequestAction {
    @SerialName("request")
    REQUEST,

    @SerialName("request_cancellation")
    REQUEST_CANCELLATION
}