package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

fun createEventSerializersModule(
    mappings: EventContentSerializerMappings,
): SerializersModule {
    val messageEventContentSerializer = MessageEventContentSerializer(mappings.message)
    val messageEventSerializer = MessageEventSerializer(mappings.message, messageEventContentSerializer)
    val stateEventContentSerializer = StateEventContentSerializer(mappings.state)
    val stateEventSerializer = StateEventSerializer(mappings.state, stateEventContentSerializer)
    val roomEventSerializer = RoomEventSerializer(messageEventSerializer, stateEventSerializer)
    val strippedStateEventSerializer = StrippedStateEventSerializer(mappings.state, stateEventContentSerializer)
    val initialStateEventSerializer = InitialStateEventSerializer(mappings.state, stateEventContentSerializer)
    val ephemeralEventSerializer = EphemeralEventSerializer(mappings.ephemeral)
    val toDeviceEventSerializer = ToDeviceEventSerializer(mappings.toDevice)
    val decryptedOlmEventSerializer =
        DecryptedOlmEventSerializer(mappings.message + mappings.state + mappings.ephemeral + mappings.toDevice)
    val decryptedMegolmEventSerializer = DecryptedMegolmEventSerializer(mappings.message, messageEventContentSerializer)
    val globalAccountDataEventSerializer = GlobalAccountDataEventSerializer(mappings.globalAccountData)
    val roomAccountDataEventSerializer = RoomAccountDataEventSerializer(mappings.roomAccountData)
    val eventSerializer = EventSerializer(
        roomEventSerializer,
        strippedStateEventSerializer,
        initialStateEventSerializer,
        ephemeralEventSerializer,
        toDeviceEventSerializer,
        globalAccountDataEventSerializer,
        roomAccountDataEventSerializer
    )
    return SerializersModule {
        contextual(eventSerializer)
        contextual(roomEventSerializer)
        contextual(messageEventContentSerializer)
        contextual(messageEventSerializer)
        contextual(stateEventContentSerializer)
        contextual(stateEventSerializer)
        contextual(strippedStateEventSerializer)
        contextual(initialStateEventSerializer)
        contextual(ephemeralEventSerializer)
        contextual(toDeviceEventSerializer)
        contextual(decryptedOlmEventSerializer)
        contextual(decryptedMegolmEventSerializer)
        contextual(globalAccountDataEventSerializer)
        contextual(roomAccountDataEventSerializer)
    }
}