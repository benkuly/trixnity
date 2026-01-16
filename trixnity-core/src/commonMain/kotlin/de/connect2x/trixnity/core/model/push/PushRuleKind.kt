package de.connect2x.trixnity.core.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PushRuleKind {
    @SerialName("override")
    OVERRIDE,

    @SerialName("content")
    CONTENT,

    @SerialName("room")
    ROOM,

    @SerialName("sender")
    SENDER,

    @SerialName("underride")
    UNDERRIDE,
}
