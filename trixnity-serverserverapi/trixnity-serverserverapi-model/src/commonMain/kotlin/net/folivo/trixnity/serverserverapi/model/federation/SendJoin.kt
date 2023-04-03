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
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit
import net.folivo.trixnity.serverserverapi.model.SignedPersistentStateDataUnit

/**
 * @see <a href="https://spec.matrix.org/v1.6/server-server-api/#put_matrixfederationv2send_joinroomideventid">matrix spec</a>
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
        json: Json
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
        @SerialName("origin")
        val origin: String,
        @SerialName("state")
        val state: List<SignedPersistentStateDataUnit<*>>,
    )
}