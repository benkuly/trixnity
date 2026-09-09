package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4140
import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.EventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlocks
import de.connect2x.trixnity.core.serialization.events.m.rtc.RtcApplicationMemberSerializer
import de.connect2x.trixnity.core.serialization.events.m.rtc.RtcApplicationSlotSerializer
import de.connect2x.trixnity.core.serialization.events.m.rtc.RtcEncryptionSerializer
import de.connect2x.trixnity.core.serialization.events.m.rtc.RtcTransportSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

fun createMatrixEventSerializersModule(mappings: EventContentSerializerMappings): SerializersModule {
    val contextualMessageEventContentSerializer = ContextualMessageEventContentSerializer(mappings.message)
    val contextualStateEventContentSerializer = ContextualStateEventContentSerializer(mappings.state)
    val messageEventSerializer = MessageEventSerializer(mappings.message)
    val stateEventSerializer = StateEventSerializer(mappings.state)
    val roomEventSerializer = RoomEventSerializer(messageEventSerializer, stateEventSerializer)
    val strippedStateEventSerializer = StrippedStateEventSerializer(mappings.state)
    val stateBaseEventSerializer = StateBaseEventSerializer(stateEventSerializer, strippedStateEventSerializer)
    val initialStateEventSerializer = InitialStateEventSerializer(mappings.state)
    @OptIn(MSC4140::class) val delayedMessageEventSerializer = DelayedMessageEventSerializer(mappings.message)
    @OptIn(MSC4140::class) val delayedStateEventSerializer = DelayedStateEventSerializer(mappings.state)
    @OptIn(MSC4140::class)
    val delayedRoomEventSerializer = DelayedEventSerializer(delayedMessageEventSerializer, delayedStateEventSerializer)
    val ephemeralEventSerializer = EphemeralEventSerializer(mappings.ephemeral)
    val toDeviceEventSerializer = ToDeviceEventSerializer(mappings.toDevice)
    val decryptedOlmEventSerializer =
        DecryptedOlmEventSerializer(
            @Suppress("UNCHECKED_CAST")
            ((mappings.message + mappings.state + mappings.ephemeral + mappings.toDevice)
                as Set<EventContentSerializerMapping<EventContent>>)
        )
    val decryptedMegolmEventSerializer = DecryptedMegolmEventSerializer(mappings.message)
    val globalAccountDataEventSerializer = GlobalAccountDataEventSerializer(mappings.globalAccountData)
    val roomAccountDataEventSerializer = RoomAccountDataEventSerializer(mappings.roomAccountData)
    @OptIn(MSC4143::class) val rtcApplicationMemberSerializer = RtcApplicationMemberSerializer(mappings.rtcApplication)
    @OptIn(MSC4143::class) val rtcApplicationSlotSerializer = RtcApplicationSlotSerializer(mappings.rtcApplication)
    @OptIn(MSC4143::class) val rtcTransportSerializer = RtcTransportSerializer(mappings.rtcTransport)
    @OptIn(MSC4143::class) val rtcEncryptionSerializer = RtcEncryptionSerializer(mappings.rtcEncryption)

    val eventContentBlocksSerializer = EventContentBlocks.Serializer(mappings.block)
    val eventTypeSerializer = EventTypeSerializer(mappings)
    return SerializersModule {
        contextual(contextualMessageEventContentSerializer)
        contextual(contextualStateEventContentSerializer)
        contextual(roomEventSerializer)
        contextual(messageEventSerializer)
        contextual(stateEventSerializer)
        contextual(strippedStateEventSerializer)
        contextual(stateBaseEventSerializer)
        contextual(initialStateEventSerializer)
        @OptIn(MSC4140::class) contextual(delayedMessageEventSerializer)
        @OptIn(MSC4140::class) contextual(delayedStateEventSerializer)
        @OptIn(MSC4140::class) contextual(delayedRoomEventSerializer)
        contextual(ephemeralEventSerializer)
        contextual(toDeviceEventSerializer)
        contextual(decryptedOlmEventSerializer)
        contextual(decryptedMegolmEventSerializer)
        contextual(globalAccountDataEventSerializer)
        contextual(roomAccountDataEventSerializer)
        @OptIn(MSC4143::class) contextual(rtcApplicationMemberSerializer)
        @OptIn(MSC4143::class) contextual(rtcApplicationSlotSerializer)
        @OptIn(MSC4143::class) contextual(rtcTransportSerializer)
        @OptIn(MSC4143::class) contextual(rtcEncryptionSerializer)
        contextual(eventContentBlocksSerializer)
        contextual(eventTypeSerializer)
    }
}
