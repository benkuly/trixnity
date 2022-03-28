package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mroomhistory_visibility">matrix spec</a>
 */
@Serializable
data class HistoryVisibilityEventContent(
    @SerialName("history_visibility")
    val historyVisibility: HistoryVisibility
) : StateEventContent {
    @Serializable
    enum class HistoryVisibility {
        @SerialName("invited")
        INVITED,

        @SerialName("joined")
        JOINED,

        @SerialName("shared")
        SHARED,

        @SerialName("world_readable")
        WORLD_READABLE
    }
}