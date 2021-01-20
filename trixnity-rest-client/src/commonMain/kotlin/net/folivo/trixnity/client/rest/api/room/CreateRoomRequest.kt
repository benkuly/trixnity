package net.folivo.trixnity.client.rest.api.room

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEvent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEvent

@Serializable
data class CreateRoomRequest(
    @SerialName("visibility") val visibility: Visibility,
    @SerialName("room_alias_name") val roomAliasLocalpart: String?,
    @SerialName("name") val name: String?,
    @SerialName("topic") val topic: String?,
    @SerialName("invite") val invite: Set<UserId>?,
    @SerialName("invite_3pid") val invite3Pid: Set<Invite3Pid>?,
    @SerialName("room_version") val roomVersion: String?,
    @SerialName("creation_content") val creationContent: CreateEvent.CreateEventContent?,
    @SerialName("initial_state") val initialState: List<StateEvent<@Contextual Any>>?,
    @SerialName("preset") val preset: Preset?,
    @SerialName("is_direct") val isDirect: Boolean?,
    @SerialName("power_level_content_override") val powerLevelContentOverride: PowerLevelsEvent.PowerLevelsEventContent?,
) {
    @Serializable
    data class Invite3Pid(
        @SerialName("id_server") val identityServer: String,
        @SerialName("id_access_token") val identityServerAccessToken: String,
        @SerialName("medium") val medium: String,
        @SerialName("address") val address: String
    )

    @Serializable
    enum class Preset {
        @SerialName("private_chat")
        PRIVATE,

        @SerialName("public_chat")
        PUBLIC,

        @SerialName("trusted_private_chat")
        TRUSTED_PRIVATE
    }
}