package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.model.events.*

class EventContentSerializerMappingsBuilder {
    val message = mutableSetOf<MessageEventContentSerializerMapping>()
    val state = mutableSetOf<StateEventContentSerializerMapping>()
    val ephemeral = mutableSetOf<EventContentSerializerMapping<EphemeralEventContent>>()
    val ephemeralDataUnit = mutableSetOf<EventContentSerializerMapping<EphemeralDataUnitContent>>()
    val toDevice = mutableSetOf<EventContentSerializerMapping<ToDeviceEventContent>>()
    val globalAccountData = mutableSetOf<EventContentSerializerMapping<GlobalAccountDataEventContent>>()
    val roomAccountData = mutableSetOf<EventContentSerializerMapping<RoomAccountDataEventContent>>()

    fun build(): EventContentSerializerMappings =
        object : EventContentSerializerMappings {
            override val message = this@EventContentSerializerMappingsBuilder.message.toSet()
            override val state = this@EventContentSerializerMappingsBuilder.state.toSet()
            override val ephemeral = this@EventContentSerializerMappingsBuilder.ephemeral.toSet()
            override val ephemeralDataUnit = this@EventContentSerializerMappingsBuilder.ephemeralDataUnit.toSet()
            override val toDevice = this@EventContentSerializerMappingsBuilder.toDevice.toSet()
            override val globalAccountData = this@EventContentSerializerMappingsBuilder.globalAccountData.toSet()
            override val roomAccountData = this@EventContentSerializerMappingsBuilder.roomAccountData.toSet()
        }
}

fun createEventContentSerializerMappings(builder: EventContentSerializerMappingsBuilder.() -> Unit): EventContentSerializerMappings =
    EventContentSerializerMappingsBuilder().apply(builder).build()

inline fun <reified C : MessageEventContent> EventContentSerializerMappingsBuilder.messageOf(
    type: String,
    serializer: KSerializer<C>
) {
    message.add(MessageEventContentSerializerMapping(type, C::class, serializer))
}

inline fun <reified C : MessageEventContent> EventContentSerializerMappingsBuilder.messageOf(
    type: String
) {
    message.add(MessageEventContentSerializerMapping(type, C::class, serializer<C>()))
}

inline fun <reified C : StateEventContent> EventContentSerializerMappingsBuilder.stateOf(
    type: String,
    serializer: KSerializer<C>
) {
    state.add(StateEventContentSerializerMapping(type, C::class, serializer))
}

inline fun <reified C : StateEventContent> EventContentSerializerMappingsBuilder.stateOf(
    type: String
) {
    state.add(StateEventContentSerializerMapping(type, C::class, serializer<C>()))
}

inline fun <reified C : EphemeralEventContent> EventContentSerializerMappingsBuilder.ephemeralOf(
    type: String,
    serializer: KSerializer<C>
) {
    ephemeral.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : EphemeralEventContent> EventContentSerializerMappingsBuilder.ephemeralOf(
    type: String
) {
    ephemeral.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : EphemeralDataUnitContent> EventContentSerializerMappingsBuilder.ephemeralDataUnitOf(
    type: String,
    serializer: KSerializer<C>
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
    serializer: KSerializer<C>
) {
    toDevice.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : ToDeviceEventContent> EventContentSerializerMappingsBuilder.toDeviceOf(
    type: String
) {
    toDevice.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}

inline fun <reified C : GlobalAccountDataEventContent> EventContentSerializerMappingsBuilder.globalAccountDataOf(
    type: String,
    serializer: KSerializer<C>
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
    serializer: KSerializer<C>
) {
    roomAccountData.add(EventContentSerializerMappingImpl(type, C::class, serializer))
}

inline fun <reified C : RoomAccountDataEventContent> EventContentSerializerMappingsBuilder.roomAccountDataOf(
    type: String
) {
    roomAccountData.add(EventContentSerializerMappingImpl(type, C::class, serializer<C>()))
}