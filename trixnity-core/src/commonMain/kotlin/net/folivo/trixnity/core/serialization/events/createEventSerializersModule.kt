package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

fun createEventSerializersModule(
    mappings: EventContentSerializerMappings,
): SerializersModule {
    val basicEventSerializer = BasicEventSerializer()
    val messageEventSerializer = MessageEventSerializer(mappings.message)
    val stateEventSerializer = StateEventSerializer(mappings.state)
    val roomEventSerializer = RoomEventSerializer(messageEventSerializer, stateEventSerializer)
    val strippedStateEventSerializer = StrippedStateEventSerializer(mappings.state)
    val initialStateEventSerializer = InitialStateEventSerializer(mappings.state)
    val ephemeralEventSerializer = EphemeralEventSerializer(mappings.ephemeral)
    val toDeviceEventSerializer = ToDeviceEventSerializer(mappings.toDevice)
    val olmEventSerializer =
        OlmEventSerializer(mappings.message + mappings.state + mappings.ephemeral + mappings.toDevice)
    val megolmEventSerializer = MegolmEventSerializer(mappings.message)
    val globalAccountDataEventSerializer = GlobalAccountDataEventSerializer(mappings.globalAccountData)
    val roomAccountDataEventSerializer = RoomAccountDataEventSerializer(mappings.roomAccountData)
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
        roomAccountDataEventSerializer
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
    }
}