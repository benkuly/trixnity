package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.PUT
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.PersistentDataUnit.PersistentStateDataUnit
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#put_matrixfederationv1exchange_third_party_inviteroomid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/exchange_third_party_invite/{roomId}")
@HttpMethod(PUT)
data class ExchangeThirdPartyInvite(
    @SerialName("roomId") val roomId: RoomId,
) : MatrixEndpoint<Signed<PersistentStateDataUnit<MemberEventContent>, String>, Unit> {
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
}