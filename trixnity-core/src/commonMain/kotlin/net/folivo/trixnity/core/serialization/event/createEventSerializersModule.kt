package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.EphemeralEventContent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import org.kodein.log.LoggerFactory
import kotlin.reflect.KClass

fun createEventSerializersModule(
    mappings: EventContentSerializerMappings,
    loggerFactory: LoggerFactory
): SerializersModule {
    val basicEventSerializer = BasicEventSerializer()
    val messageEventSerializer = MessageEventSerializer(mappings.room, loggerFactory)
    val stateEventSerializer = StateEventSerializer(mappings.state, loggerFactory)
    val roomEventSerializer = RoomEventSerializer(messageEventSerializer, stateEventSerializer, loggerFactory)
    val strippedStateEventSerializer = StrippedStateEventSerializer(mappings.state, loggerFactory)
    val ephemeralEventSerializer = EphemeralEventSerializer(mappings.ephemeral, loggerFactory)
    val toDeviceEventSerializer = ToDeviceEventSerializer(mappings.toDevice, loggerFactory)
    val olmEventSerializer =
        OlmEventSerializer(mappings.room + mappings.state + mappings.ephemeral + mappings.toDevice, loggerFactory)
    val megolmEventSerializer = MegolmEventSerializer(mappings.room, loggerFactory)
    val eventSerializer = EventSerializer(
        basicEventSerializer,
        roomEventSerializer,
        strippedStateEventSerializer,
        ephemeralEventSerializer,
        toDeviceEventSerializer,
        olmEventSerializer,
        megolmEventSerializer,
        loggerFactory
    )
    return SerializersModule {
        contextual(basicEventSerializer)
        contextual(eventSerializer)
        contextual(roomEventSerializer)
        contextual(messageEventSerializer)
        contextual(stateEventSerializer)
        contextual(strippedStateEventSerializer)
        contextual(ephemeralEventSerializer)
        contextual(toDeviceEventSerializer)
        contextual(olmEventSerializer)
        contextual(megolmEventSerializer)

        mappings.room.forEach {
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            contextual(it.kClass as KClass<MessageEventContent>, it.serializer as KSerializer<MessageEventContent>)
        }
        mappings.state.forEach {
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            contextual(it.kClass as KClass<StateEventContent>, it.serializer as KSerializer<StateEventContent>)
        }
        mappings.ephemeral.forEach {
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            contextual(it.kClass as KClass<EphemeralEventContent>, it.serializer as KSerializer<EphemeralEventContent>)
        }
        mappings.toDevice.forEach {
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            contextual(it.kClass as KClass<ToDeviceEventContent>, it.serializer as KSerializer<ToDeviceEventContent>)
        }
    }
}