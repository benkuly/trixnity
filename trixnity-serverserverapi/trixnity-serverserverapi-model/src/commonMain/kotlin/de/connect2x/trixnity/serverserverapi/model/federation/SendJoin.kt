package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.PUT
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.serverserverapi.model.SignedPersistentDataUnit
import de.connect2x.trixnity.serverserverapi.model.SignedPersistentStateDataUnit

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#put_matrixfederationv2send_joinroomideventid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v2/send_join/{roomId}/{eventId}")
@HttpMethod(PUT)
data class SendJoin(
    @SerialName("roomId") val roomId: RoomId,
    @SerialName("eventId") val eventId: EventId,
    @SerialName("omit_members") val omitMembers: Boolean? = null,
) : MatrixEndpoint<Signed<PersistentStateDataUnit<MemberEventContent>, String>, SendJoin.Response> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Signed<PersistentStateDataUnit<MemberEventContent>, String>?
    ): KSerializer<Signed<PersistentStateDataUnit<MemberEventContent>, String>> {
        @Suppress("UNCHECKED_CAST")
        val serializer = requireNotNull(json.serializersModule.getContextual(PersistentStateDataUnit::class))
                as KSerializer<PersistentStateDataUnit<MemberEventContent>>
        return Signed.serializer(serializer, String.serializer())
    }

    @Serializable
    data class Response(
        @SerialName("auth_chain")
        val authChain: List<SignedPersistentDataUnit<*>>,
        @SerialName("event")
        val event: Signed<@Contextual PersistentStateDataUnit<MemberEventContent>, String>? = null,
        @SerialName("state")
        val state: List<SignedPersistentStateDataUnit<*>>,
    )
}