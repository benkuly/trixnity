package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyAlgorithm.Unknown

sealed interface Key {
    val algorithm: KeyAlgorithm
    val keyId: String?
    val value: String

    val fullKeyId: String?
        get() = if (keyId != null) "${algorithm.name}:$keyId" else null

    @Serializable(with = Ed25519KeySerializer::class)
    data class Ed25519Key(
        override val keyId: String? = null,
        override val value: String,
        override val algorithm: KeyAlgorithm.Ed25519 = KeyAlgorithm.Ed25519,
    ) : Key

    @Serializable(with = Curve25519KeySerializer::class)
    data class Curve25519Key(
        override val keyId: String? = null,
        override val value: String,
        override val algorithm: KeyAlgorithm.Curve25519 = KeyAlgorithm.Curve25519,
    ) : Key

    class SignedCurve25519Key(
        override val keyId: String? = null,
        override val value: String,
        signatures: Signatures<UserId>,
        override val algorithm: KeyAlgorithm.SignedCurve25519 = KeyAlgorithm.SignedCurve25519,
    ) : Key, Signed<Curve25519Key, UserId>(Curve25519Key(keyId, value), signatures)

    data class UnknownKey(
        override val keyId: String? = null,
        override val value: String,
        val raw: JsonElement,
        override val algorithm: Unknown,
    ) : Key
}

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