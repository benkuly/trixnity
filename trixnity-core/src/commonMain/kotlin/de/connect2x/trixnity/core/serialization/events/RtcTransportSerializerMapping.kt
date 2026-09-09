package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

@MSC4143
interface RtcTransportSerializerMapping<T : RtcTransport> {
    val type: String
    val kClass: KClass<out T>
    val serializer: KSerializer<T>
}

@MSC4143
data class RtcTransportSerializerMappingImpl<T : RtcTransport>(
    override val type: String,
    override val kClass: KClass<out T>,
    override val serializer: KSerializer<T>,
) : RtcTransportSerializerMapping<T>
