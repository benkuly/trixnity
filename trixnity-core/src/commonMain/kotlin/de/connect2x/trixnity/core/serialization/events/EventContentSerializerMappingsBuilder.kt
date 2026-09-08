package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.EphemeralDataUnitContent
import de.connect2x.trixnity.core.model.events.EphemeralEventContent
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.block.EventContentBlock
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationSlot
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import de.connect2x.trixnity.core.model.events.m.rtc.RtcTransport
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

class EventContentSerializerMappingsBuilder {
    val message = mutableSetOf<MessageEventContentSerializerMapping>()
    val state = mutableSetOf<StateEventContentSerializerMapping>()
    val ephemeral = mutableSetOf<EventContentSerializerMapping<EphemeralEventContent>>()
    val ephemeralDataUnit = mutableSetOf<EventContentSerializerMapping<EphemeralDataUnitContent>>()
    val toDevice = mutableSetOf<EventContentSerializerMapping<ToDeviceEventContent>>()
    val globalAccountData = mutableSetOf<EventContentSerializerMapping<GlobalAccountDataEventContent>>()
    val roomAccountData = mutableSetOf<EventContentSerializerMapping<RoomAccountDataEventContent>>()

    @MSC4143 val rtcApplication = mutableSetOf<RtcApplicationSerializerMapping<*, *>>()
    @MSC4143 val rtcTransport = mutableSetOf<RtcTransportSerializerMapping<*>>()
    @MSC4143 val rtcEncryption = mutableSetOf<RtcEncryptionSerializerMapping<*>>()

    val block = mutableSetOf<EventContentBlockSerializerMapping<*>>()

    @OptIn(MSC4143::class)
    fun build(): EventContentSerializerMappings =
        object : EventContentSerializerMappings {
            override val message = this@EventContentSerializerMappingsBuilder.message.toSet()
            override val state = this@EventContentSerializerMappingsBuilder.state.toSet()
            override val ephemeral = this@EventContentSerializerMappingsBuilder.ephemeral.toSet()
            override val ephemeralDataUnit = this@EventContentSerializerMappingsBuilder.ephemeralDataUnit.toSet()
            override val toDevice = this@EventContentSerializerMappingsBuilder.toDevice.toSet()
            override val globalAccountData = this@EventContentSerializerMappingsBuilder.globalAccountData.toSet()
            override val roomAccountData = this@EventContentSerializerMappingsBuilder.roomAccountData.toSet()
            override val rtcApplication = this@EventContentSerializerMappingsBuilder.rtcApplication.toSet()
            override val rtcTransport = this@EventContentSerializerMappingsBuilder.rtcTransport.toSet()
            override val rtcEncryption = this@EventContentSerializerMappingsBuilder.rtcEncryption.toSet()
            override val block = this@EventContentSerializerMappingsBuilder.block.toSet()
        }
}

operator fun EventContentSerializerMappings.Companion.invoke(
    builder: EventContentSerializerMappingsBuilder.() -> Unit
): EventContentSerializerMappings = EventContentSerializerMappingsBuilder().apply(builder).build()

inline fun <reified C : MessageEventContent> EventContentSerializerMappingsBuilder.messageOf(
    type: String,
    serializer: KSerializer<C>,
) {
    message.add(MessageEventContentSerializerMapping(type, C::class, serializer))
}

inline fun <reified C : MessageEventContent> EventContentSerializerMappingsBuilder.messageOf(type: String) {
    message.add(MessageEventContentSerializerMapping(type, C::class, serializer<C>()))
}

inline fun <reified C : StateEventContent> EventContentSerializerMappingsBuilder.stateOf(
    type: String,
    serializer: KSerializer<C>,
) {
    state.add(StateEventContentSerializerMapping(type, C::class, serializer))
}

inline fun <reified C : StateEventContent> EventContentSerializerMappingsBuilder.stateOf(type: String) {
    state.add(StateEventContentSerializerMapping(type, C::class, serializer<C>()))
}

inline fun <reified C : EphemeralEventContent> EventContentSerializerMappingsBuilder.ephemeralOf(
    type: String,
    serializer: KSerializer<C>,
) {
    ephemeral.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : EphemeralEventContent> EventContentSerializerMappingsBuilder.ephemeralOf(type: String) {
    ephemeral.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : EphemeralDataUnitContent> EventContentSerializerMappingsBuilder.ephemeralDataUnitOf(
    type: String,
    serializer: KSerializer<C>,
) {
    ephemeralDataUnit.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : EphemeralDataUnitContent> EventContentSerializerMappingsBuilder.ephemeralDataUnitOf(
    type: String
) {
    ephemeralDataUnit.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : ToDeviceEventContent> EventContentSerializerMappingsBuilder.toDeviceOf(
    type: String,
    serializer: KSerializer<C>,
) {
    toDevice.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : ToDeviceEventContent> EventContentSerializerMappingsBuilder.toDeviceOf(type: String) {
    toDevice.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : GlobalAccountDataEventContent> EventContentSerializerMappingsBuilder.globalAccountDataOf(
    type: String,
    serializer: KSerializer<C>,
) {
    globalAccountData.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : GlobalAccountDataEventContent> EventContentSerializerMappingsBuilder.globalAccountDataOf(
    type: String
) {
    globalAccountData.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : RoomAccountDataEventContent> EventContentSerializerMappingsBuilder.roomAccountDataOf(
    type: String,
    serializer: KSerializer<C>,
) {
    roomAccountData.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : RoomAccountDataEventContent> EventContentSerializerMappingsBuilder.roomAccountDataOf(
    type: String
) {
    roomAccountData.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : EventContentBlock> EventContentSerializerMappingsBuilder.blockOf(
    type: EventContentBlock.Type<C>,
    serializer: KSerializer<C>,
) {
    block.add(EventContentBlockSerializerMappingImpl(type, C::class, serializer))
}

@MSC4143
inline fun <reified S : RtcApplicationSlot, reified M : RtcApplicationMember> EventContentSerializerMappingsBuilder
    .rtcApplicationOf(type: String) {
    rtcApplication.add(
        RtcApplicationSerializerMappingImpl(
            type = type,
            applicationClass = S::class,
            applicationSerializer = serializer<S>(),
            memberClass = M::class,
            memberSerializer = serializer<M>(),
        )
    )
}

@MSC4143
inline fun <reified C : RtcTransport> EventContentSerializerMappingsBuilder.rtcTransportOf(type: String) {
    rtcTransport.add(RtcTransportSerializerMappingImpl(type, C::class, serializer<C>()))
}

@MSC4143
inline fun <reified C : RtcEncryption> EventContentSerializerMappingsBuilder.rtcEncryptionOf(type: String) {
    rtcEncryption.add(RtcEncryptionSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : EventContentBlock> EventContentSerializerMappingsBuilder.blockOf(
    type: EventContentBlock.Type<C>
) {
    block.add(EventContentBlockSerializerMappingImpl(type, C::class, serializer<C>()))
}
