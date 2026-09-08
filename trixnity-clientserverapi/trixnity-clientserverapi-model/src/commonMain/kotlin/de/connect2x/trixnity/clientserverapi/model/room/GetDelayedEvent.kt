package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.events.DelayedEvent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@MSC4140
@Serializable
@Resource("/_matrix/client/unstable/org.matrix.msc4140/delayed_events/{delay_id}")
@HttpMethod(GET)
data class GetDelayedEvent(@SerialName("delay_id") val delayId: String) : MatrixEndpoint<Unit, DelayedEvent<*>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: DelayedEvent<*>?,
    ): KSerializer<DelayedEvent<*>> {
        return requireNotNull(json.serializersModule.getContextual(DelayedEvent::class))
    }
}
