package net.folivo.trixnity.core.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushRuleSet(
    @SerialName("content")
    val content: List<PushRule>? = listOf(),
    @SerialName("override")
    val override: List<PushRule>? = listOf(),
    @SerialName("room")
    val room: List<PushRule>? = listOf(),
    @SerialName("sender")
    val sender: List<PushRule>? = listOf(),
    @SerialName("underride")
    val underride: List<PushRule>? = listOf(),
)
