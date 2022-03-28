package net.folivo.trixnity.core.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PushRuleKind {
    @SerialName("content")
    CONTENT,

    @SerialName("override")
    OVERRIDE,

    @SerialName("room")
    ROOM,

    @SerialName("sender")
    SENDER,

    @SerialName("underride")
    UNDERRIDE,
}
