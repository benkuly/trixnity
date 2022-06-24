package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#mtag">matrix spec</a>
 */
@Serializable
data class TagEventContent(
    @SerialName("tags") val tags: Map<String, Tag>,
) : RoomAccountDataEventContent {
    @Serializable
    data class Tag(
        @SerialName("order") val order: Double? = null
    )
}