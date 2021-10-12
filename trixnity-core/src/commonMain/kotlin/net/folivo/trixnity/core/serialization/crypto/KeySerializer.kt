package net.folivo.trixnity.core.serialization.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.crypto.Key

object Ed25519KeySerializer : KSerializer<Key.Ed25519Key> {
    override fun deserialize(decoder: Decoder): Key.Ed25519Key {
        require(decoder is JsonDecoder)
        return Key.Ed25519Key(null, decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Key.Ed25519Key) {
        require(encoder is JsonEncoder)
        encoder.encodeString(value.value)
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Ed25519KeySerializer")
}

object Curve25519KeySerializer : KSerializer<Key.Curve25519Key> {
    override fun deserialize(decoder: Decoder): Key.Curve25519Key {
        require(decoder is JsonDecoder)
        return Key.Curve25519Key(null, decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Key.Curve25519Key) {
        require(encoder is JsonEncoder)
        encoder.encodeString(value.value)
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Ed25519KeySerializer")
}