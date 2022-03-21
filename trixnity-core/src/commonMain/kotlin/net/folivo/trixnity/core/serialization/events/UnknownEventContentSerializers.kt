package net.folivo.trixnity.core.serialization.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.core.model.events.*


class UnknownEventContentSerializer(val eventType: String) : KSerializer<UnknownEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownEventContent {
        require(decoder is JsonDecoder)
        return UnknownEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}

class UnknownEphemeralEventContentSerializer(val eventType: String) : KSerializer<UnknownEphemeralEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownEphemeralEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownEphemeralEventContent {
        require(decoder is JsonDecoder)
        return UnknownEphemeralEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownEphemeralEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}

class UnknownGlobalAccountDataEventContentSerializer(val eventType: String) :
    KSerializer<UnknownGlobalAccountDataEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownGlobalAccountDataEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownGlobalAccountDataEventContent {
        require(decoder is JsonDecoder)
        return UnknownGlobalAccountDataEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownGlobalAccountDataEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}

class UnknownMessageEventContentSerializer(val eventType: String) : KSerializer<UnknownMessageEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownMessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownMessageEventContent {
        require(decoder is JsonDecoder)
        return UnknownMessageEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownMessageEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}

class UnknownRoomAccountDataEventContentSerializer(val eventType: String) :
    KSerializer<UnknownRoomAccountDataEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownRoomAccountDataEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownRoomAccountDataEventContent {
        require(decoder is JsonDecoder)
        return UnknownRoomAccountDataEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownRoomAccountDataEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}

class UnknownRoomEventContentSerializer(val eventType: String) : KSerializer<UnknownRoomEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownRoomEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownRoomEventContent {
        require(decoder is JsonDecoder)
        return UnknownRoomEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownRoomEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}

class UnknownStateEventContentSerializer(val eventType: String) : KSerializer<UnknownStateEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownStateEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownStateEventContent {
        require(decoder is JsonDecoder)
        return UnknownStateEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownStateEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}

class UnknownToDeviceEventContentSerializer(val eventType: String) : KSerializer<UnknownToDeviceEventContent> {
    override val descriptor = buildClassSerialDescriptor("UnknownToDeviceEventContentSerializer")

    override fun deserialize(decoder: Decoder): UnknownToDeviceEventContent {
        require(decoder is JsonDecoder)
        return UnknownToDeviceEventContent(decoder.decodeJsonElement().jsonObject, eventType)
    }

    override fun serialize(encoder: Encoder, value: UnknownToDeviceEventContent) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(value.raw)
    }
}