package de.connect2x.trixnity.core.model.keys

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
import kotlinx.serialization.json.JsonElement
import de.connect2x.trixnity.core.model.UserId

sealed interface KeyValue {
    val value: String

    @Serializable(with = Ed25519KeyValue.Serializer::class)
    data class Ed25519KeyValue(
        override val value: String,
    ) : KeyValue {
        object Serializer : UnwrapSerializer<Ed25519KeyValue>("Ed25519KeyValue", ::Ed25519KeyValue)
    }

    @Serializable(with = Curve25519KeyValue.Serializer::class)
    data class Curve25519KeyValue(
        override val value: String,
    ) : KeyValue {
        object Serializer : UnwrapSerializer<Curve25519KeyValue>("Curve25519KeyValue", ::Curve25519KeyValue)
    }

    @Serializable(with = SignedCurve25519KeyValue.Serializer::class)
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

        object Serializer : KSerializer<SignedCurve25519KeyValue> {
            private val delegate = Signed.Serializer(
                SignedCurve25519KeyValueSignable.serializer(),
                UserId.serializer(),
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
    }

    data class UnknownKeyValue(
        override val value: String,
        val raw: JsonElement,
    ) : KeyValue

    abstract class UnwrapSerializer<T : KeyValue>(
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
    }

    companion object {}
}