package net.folivo.trixnity.core.serialization.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.*
import org.kodein.log.LoggerFactory
import kotlin.reflect.KClass

fun createEventSerializersModule(
    mappings: EventContentSerializerMappings,
    loggerFactory: LoggerFactory
): SerializersModule {
    val basicEventSerializer = BasicEventSerializer()
    val messageEventSerializer = MessageEventSerializer(mappings.message, loggerFactory)
    val stateEventSerializer = StateEventSerializer(mappings.state, loggerFactory)
    val roomEventSerializer = RoomEventSerializer(messageEventSerializer, stateEventSerializer, loggerFactory)
    val strippedStateEventSerializer = StrippedStateEventSerializer(mappings.state, loggerFactory)
    val initialStateEventSerializer = InitialStateEventSerializer(mappings.state, loggerFactory)
    val ephemeralEventSerializer = EphemeralEventSerializer(mappings.ephemeral, loggerFactory)
    val toDeviceEventSerializer = ToDeviceEventSerializer(mappings.toDevice, loggerFactory)
    val olmEventSerializer =
        OlmEventSerializer(mappings.message + mappings.state + mappings.ephemeral + mappings.toDevice, loggerFactory)
    val megolmEventSerializer = MegolmEventSerializer(mappings.message, loggerFactory)
    val globalAccountDataEventSerializer = GlobalAccountDataEventSerializer(mappings.globalAccountData, loggerFactory)
    val roomAccountDataEventSerializer = RoomAccountDataEventSerializer(mappings.roomAccountData, loggerFactory)
    val eventSerializer = EventSerializer(
        basicEventSerializer,
        roomEventSerializer,
        strippedStateEventSerializer,
        initialStateEventSerializer,
        ephemeralEventSerializer,
        toDeviceEventSerializer,
        olmEventSerializer,
        megolmEventSerializer,
        globalAccountDataEventSerializer,
        roomAccountDataEventSerializer,
        loggerFactory
    )
    return SerializersModule {
        contextual(basicEventSerializer)
        contextual(eventSerializer)
        contextual(roomEventSerializer)
        contextual(messageEventSerializer)
        contextual(stateEventSerializer)
        contextual(strippedStateEventSerializer)
        contextual(initialStateEventSerializer)
        contextual(ephemeralEventSerializer)
        contextual(toDeviceEventSerializer)
        contextual(olmEventSerializer)
        contextual(megolmEventSerializer)
        contextual(globalAccountDataEventSerializer)
        contextual(roomAccountDataEventSerializer)

        mappings.message.forEach {
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
        mappings.globalAccountData.forEach {
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            contextual(
                it.kClass as KClass<GlobalAccountDataEventContent>,
                it.serializer as KSerializer<GlobalAccountDataEventContent>
            )
        }
        mappings.roomAccountData.forEach {
            @Suppress("UNCHECKED_CAST") // TODO unchecked cast
            contextual(
                it.kClass as KClass<RoomAccountDataEventContent>,
                it.serializer as KSerializer<RoomAccountDataEventContent>
            )
        }
    }
}