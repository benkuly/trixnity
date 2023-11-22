package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.7/server-server-api/#put_matrixfederationv1send_knockroomideventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/send_knock/{roomId}/{eventId}")
@HttpMethod(PUT)
data class SendKnock(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
) : MatrixEndpoint<Signed<PersistentStateDataUnit<MemberEventContent>, String>, SendKnock.Response> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Signed<PersistentStateDataUnit<MemberEventContent>, String>?
    ): KSerializer<Signed<PersistentStateDataUnit<MemberEventContent>, String>>? {
        @Suppress("UNCHECKED_CAST")
        val serializer = requireNotNull(json.serializersModule.getContextual(PersistentStateDataUnit::class))
                as KSerializer<PersistentStateDataUnit<MemberEventContent>>
        return Signed.serializer(serializer, String.serializer())
    }

    @Serializable
    data class Response(
        @SerialName("knock_room_state")
        val knockRoomState: List<@Contextual StrippedStateEvent<*>>,
    )
}