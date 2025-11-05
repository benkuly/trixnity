package net.folivo.trixnity.core.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushRuleSet(
    @SerialName("override")
    val override: List<PushRule.Override>? = null,
    @SerialName("content")
    val content: List<PushRule.Content>? = null,
    @SerialName("room")
    val room: List<PushRule.Room>? = null,
    @SerialName("sender")
    val sender: List<PushRule.Sender>? = null,
    @SerialName("underride")
    val underride: List<PushRule.Underride>? = null,
)

fun PushRuleSet.toList() =
    override.orEmpty() +
            content.orEmpty() +
            room.orEmpty() +
            sender.orEmpty() +
            underride.orEmpty()
