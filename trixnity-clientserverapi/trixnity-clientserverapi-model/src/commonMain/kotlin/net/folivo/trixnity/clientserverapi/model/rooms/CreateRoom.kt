package net.folivo.trixnity.clientserverapi.model.rooms

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#post_matrixclientv3createroom">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/createRoom")
@HttpMethod(POST)
data class CreateRoom(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<CreateRoom.Request, CreateRoom.Response> {

    @Serializable
    data class Request(
        @SerialName("visibility") val visibility: DirectoryVisibility,
        @SerialName("room_alias_name") val roomAliasLocalPart: String?,
        @SerialName("name") val name: String?,
        @SerialName("topic") val topic: String?,
        @SerialName("invite") val invite: Set<UserId>?,
        @SerialName("invite_3pid") val inviteThirdPid: Set<InviteThirdPid>?,
        @SerialName("room_version") val roomVersion: String?,
        @SerialName("creation_content") val creationContent: CreateEventContent?,
        @SerialName("initial_state") val initialState: List<@Contextual InitialStateEvent<*>>?,
        @SerialName("preset") val preset: Preset?,
        @SerialName("is_direct") val isDirect: Boolean?,
        @SerialName("power_level_content_override") val powerLevelContentOverride: PowerLevelsEventContent?,
    ) {
        @Serializable
        data class InviteThirdPid(
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

    @Serializable
    data class Response(
        @SerialName("room_id") val roomId: RoomId
    )
}