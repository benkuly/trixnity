package net.folivo.trixnity.core.model.events.block

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import net.folivo.trixnity.core.serialization.events.EventContentBlockSerializerMapping
import kotlin.jvm.JvmInline
import kotlin.reflect.KClass

private val log = KotlinLogging.logger("net.folivo.trixnity.core.model.events.block.EventContentBlocks")

@JvmInline
value class EventContentBlocks internal constructor(
    private val blocks: Map<EventContentBlock.Type<*>, EventContentBlock>
) {
    constructor(blocks: Set<EventContentBlock>) : this(blocks.associateBy { it.type })
    constructor(vararg blocks: EventContentBlock) : this(blocks.toSet())

    operator fun <T : EventContentBlock> get(type: EventContentBlock.Type<T>): T? {
        val block = blocks[type] ?: return null
        @Suppress("UNCHECKED_CAST")
        return block as? T
    }

    fun getUnknown(type: String): EventContentBlock.Unknown? {
        return blocks[EventContentBlock.Unknown.Type(type)] as? EventContentBlock.Unknown
    }

    operator fun plus(other: EventContentBlock): EventContentBlocks = EventContentBlocks(blocks + (other.type to other))
    operator fun minus(other: EventContentBlock): EventContentBlocks = EventContentBlocks(blocks - other.type)

    val size: Int get() = blocks.size
    fun isEmpty(): Boolean = blocks.isEmpty()
    fun containsType(key: EventContentBlock.Type<*>): Boolean = blocks.containsKey(key)
    fun contains(block: EventContentBlock): Boolean = blocks.containsValue(block)
    val types: Set<EventContentBlock.Type<*>> get() = blocks.keys
    val values: Collection<EventContentBlock> get() = blocks.values

    class Serializer(private val mappings: Set<EventContentBlockSerializerMapping<*>>) :
        KSerializer<EventContentBlocks> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EventContentBlocks")

        override fun deserialize(decoder: Decoder): EventContentBlocks {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement()
            check(jsonObject is JsonObject) { "EventContentBlocks should be deserialized from JsonObject, but was ${jsonObject::class}" }
            return EventContentBlocks(
                jsonObject
                    .mapValues { (key, value) ->
                        val serializer = mappings.find { it.type.value == key }?.serializer
                        if (serializer != null) {
                            try {
                                decoder.json.decodeFromJsonElement(serializer, value)
                            } catch (e: Exception) {
                                log.warn(e) { "malformed block $key" }
                                EventContentBlock.Unknown(key, value)
                            }
                        } else {
                            EventContentBlock.Unknown(key, value)
                        }
                    }.mapKeys { (_, value) -> value.type }
            )
        }

        override fun serialize(
            encoder: Encoder,
            value: EventContentBlocks
        ) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(
                JsonObject(
                    value.blocks
                        .mapKeys { (key, _) -> key.value }
                        .mapValues { (_, value) ->
                            val serializer = mappings.find { it.kClass.isInstance(value) }?.serializer
                                ?: throw UnsupportedEventContentBlockTypeException(value::class)
                            @Suppress("UNCHECKED_CAST")
                            serializer as KSerializer<EventContentBlock>
                            encoder.json.encodeToJsonElement(serializer, value)
                        }
                )
            )
        }
    }
}

class UnsupportedEventContentBlockTypeException(blockType: KClass<*>) : SerializationException(
    "Event content block type $blockType is not supported. If it is a custom type, you should register it!"
)