package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.core.model.events.EventContent

fun createEventSerializersModule(
    mappings: EventContentSerializerMappings,
): SerializersModule {
    val contextualMessageEventContentSerializer = ContextualMessageEventContentSerializer(mappings.message)
    val messageEventSerializer = MessageEventSerializer(mappings.message)
    val stateEventSerializer = StateEventSerializer(mappings.state)
    val roomEventSerializer = RoomEventSerializer(messageEventSerializer, stateEventSerializer)
    val strippedStateEventSerializer = StrippedStateEventSerializer(mappings.state)
    val stateBaseEventSerializer = StateBaseEventSerializer(stateEventSerializer, strippedStateEventSerializer)
    val initialStateEventSerializer = InitialStateEventSerializer(mappings.state)
    val ephemeralEventSerializer = EphemeralEventSerializer(mappings.ephemeral)
    val toDeviceEventSerializer = ToDeviceEventSerializer(mappings.toDevice)
    val decryptedOlmEventSerializer =
        DecryptedOlmEventSerializer(
            @Suppress("UNCHECKED_CAST")
            ((mappings.message + mappings.state + mappings.ephemeral + mappings.toDevice) as Set<EventContentSerializerMapping<EventContent>>)
        )
    val decryptedMegolmEventSerializer = DecryptedMegolmEventSerializer(mappings.message)
    val globalAccountDataEventSerializer = GlobalAccountDataEventSerializer(mappings.globalAccountData)
    val roomAccountDataEventSerializer = RoomAccountDataEventSerializer(mappings.roomAccountData)
    val eventTypeSerializer = EventTypeSerializer(mappings)
    return SerializersModule {
        contextual(contextualMessageEventContentSerializer)
        contextual(roomEventSerializer)
        contextual(messageEventSerializer)
        contextual(stateEventSerializer)
        contextual(strippedStateEventSerializer)
        contextual(stateBaseEventSerializer)
        contextual(initialStateEventSerializer)
        contextual(ephemeralEventSerializer)
        contextual(toDeviceEventSerializer)
        contextual(decryptedOlmEventSerializer)
        contextual(decryptedMegolmEventSerializer)
        contextual(globalAccountDataEventSerializer)
        contextual(roomAccountDataEventSerializer)
        contextual(eventTypeSerializer)
    }
}