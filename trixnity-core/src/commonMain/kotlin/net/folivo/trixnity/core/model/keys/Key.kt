package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.UserIdSerializer
import net.folivo.trixnity.core.model.keys.KeyAlgorithm.Unknown
import net.folivo.trixnity.core.model.keys.KeyValue.*
import net.folivo.trixnity.utils.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.utils.encodeUnpaddedBase64


sealed interface Key {
    val algorithm: KeyAlgorithm
    val id: String?
    val value: KeyValue

    val fullId: String
        get() = if (id.isNullOrEmpty()) algorithm.name else "${algorithm.name}:$id"

    data class Ed25519Key(
        override val id: String?,
        override val value: Ed25519KeyValue,
    ) : Key {
        constructor(id: String?, value: String) : this(id, Ed25519KeyValue(value))

        override val algorithm: KeyAlgorithm.Ed25519 = KeyAlgorithm.Ed25519
    }

    data class Curve25519Key(
        override val id: String?,
        override val value: Curve25519KeyValue,
    ) : Key {
        constructor(id: String?, value: String) : this(id, Curve25519KeyValue(value))

        override val algorithm: KeyAlgorithm.Curve25519 = KeyAlgorithm.Curve25519
    }

    data class SignedCurve25519Key(
        override val id: String?,
        override val value: SignedCurve25519KeyValue,
    ) : Key {
        constructor(id: String?, value: String, fallback: Boolean? = null, signatures: Signatures<UserId>)
                : this(id, SignedCurve25519KeyValue(value, fallback, signatures))

        override val algorithm: KeyAlgorithm.SignedCurve25519 = KeyAlgorithm.SignedCurve25519
    }

    data class UnknownKey(
        override val id: String?,
        override val value: UnknownKeyValue,
        override val algorithm: Unknown,
    ) : Key

    companion object
}

sealed interface KeyValue {
    val value: String

    @Serializable(with = UnwrapKeyValueSerializer.Ed25519KeyValueSerializer::class)
    data class Ed25519KeyValue(
        override val value: String,
    ) : KeyValue

    @Serializable(with = UnwrapKeyValueSerializer.Curve25519KeyValueSerializer::class)
    data class Curve25519KeyValue(
        override val value: String,
    ) : KeyValue

    @Serializable(with = UnwrapKeyValueSerializer.SignedCurve25519KeyValueSerializer::class)
    class SignedCurve25519KeyValue(
        override val value: String,
        val fallback: Boolean? = null,
        override val signatures: Signatures<UserId>,
    ) : KeyValue, Signed<SignedCurve25519KeyValue.SignedCurve25519KeyValueSignable, UserId>(
        SignedCurve25519KeyValueSignable(value, fallback),
        signatures
    ) {
        @Serializable
        data class SignedCurve25519KeyValueSignable(
            @SerialName("key") val key: String,
            @SerialName("fallback") val fallback: Boolean? = null,
        )
    }

    @Serializable(with = UnwrapKeyValueSerializer.UnknownKeyValueSerializer::class)
    data class UnknownKeyValue(
        override val value: String,
        val raw: JsonElement,
    ) : KeyValue

    companion object
}

abstract class UnwrapKeyValueSerializer<T : KeyValue>(
    name: String,
    private val builder: (String) -> T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        return builder(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(value.value)
    }

    object Ed25519KeyValueSerializer : UnwrapKeyValueSerializer<Ed25519KeyValue>("Ed25519KeyValue", ::Ed25519KeyValue)
    object Curve25519KeyValueSerializer :
        UnwrapKeyValueSerializer<Curve25519KeyValue>("Curve25519KeyValue", ::Curve25519KeyValue)

    object SignedCurve25519KeyValueSerializer : KSerializer<SignedCurve25519KeyValue> {
        private val delegate = SignedSerializer(
            SignedCurve25519KeyValue.SignedCurve25519KeyValueSignable.serializer(),
            UserIdSerializer,
        )
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SignedCurve25519KeyValue")

        override fun deserialize(decoder: Decoder): SignedCurve25519KeyValue {
            val signed = delegate.deserialize(decoder)
            return SignedCurve25519KeyValue(
                signed.signed.key,
                signed.signed.fallback,
                signed.signatures ?: throw SerializationException("no signatures found for curve25519 key")
            )
        }

        override fun serialize(encoder: Encoder, value: SignedCurve25519KeyValue) {
            delegate.serialize(encoder, value)
        }
    }

    object UnknownKeyValueSerializer : KSerializer<UnknownKeyValue> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownKeyValue")

        override fun deserialize(decoder: Decoder): UnknownKeyValue {
            require(decoder is JsonDecoder)
            val raw = decoder.decodeJsonElement()
            return UnknownKeyValue((raw as? JsonPrimitive)?.content ?: "unknown", raw)
        }

        override fun serialize(encoder: Encoder, value: UnknownKeyValue) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(value.raw)
        }
    }
}