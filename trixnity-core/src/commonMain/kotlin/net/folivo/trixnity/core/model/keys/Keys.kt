package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = KeysSerializer::class)
data class Keys(
    val keys: Set<Key>
) : Set<Key> {
    override val size: Int = keys.size
    override fun contains(element: Key): Boolean = keys.contains(element)
    override fun containsAll(elements: Collection<Key>): Boolean = keys.containsAll(elements)
    override fun isEmpty(): Boolean = keys.isEmpty()
    override fun iterator(): Iterator<Key> = keys.iterator()
}

fun keysOf(vararg keys: Key) = Keys(keys.toSet())

object KeysSerializer : KSerializer<Keys> {
    override fun deserialize(decoder: Decoder): Keys {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return Keys(
            jsonObj.map {
                val algorithm = KeyAlgorithm.of(it.key.substringBefore(":"))
                val keyId = it.key.substringAfter(":", "")
                    .let { foundKeyId -> foundKeyId.ifEmpty { null } }
                when (algorithm) {
                    KeyAlgorithm.Ed25519 -> Key.Ed25519Key(keyId, it.value.jsonPrimitive.content)
                    KeyAlgorithm.Curve25519 -> Key.Curve25519Key(keyId, it.value.jsonPrimitive.content)
                    KeyAlgorithm.SignedCurve25519 -> {
                        val value = it.value.jsonObject["key"]?.jsonPrimitive?.content
                        val signatures = it.value.jsonObject["signatures"]
                        requireNotNull(value)
                        requireNotNull(signatures)
                        Key.SignedCurve25519Key(keyId, value, decoder.json.decodeFromJsonElement(signatures))
                    }
                    is KeyAlgorithm.Unknown -> Key.UnknownKey(
                        keyId,
                        decoder.json.encodeToString(it.value),
                        it.value,
                        KeyAlgorithm.Unknown(algorithm.name)
                    )
                }
            }.toSet()
        )
    }

    override fun serialize(encoder: Encoder, value: Keys) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(
            JsonObject(value.keys.associate { key ->
                when (key) {
                    is Key.Ed25519Key ->
                        "${key.algorithm}" + (key.keyId?.let { ":$it" } ?: "") to JsonPrimitive(key.value)
                    is Key.Curve25519Key ->
                        "${key.algorithm}" + (key.keyId?.let { ":$it" } ?: "") to JsonPrimitive(key.value)
                    is Key.SignedCurve25519Key ->
                        "${key.algorithm}" + (key.signed.keyId?.let { ":$it" } ?: "") to JsonObject(
                            mapOf(
                                "key" to JsonPrimitive(key.signed.value),
                                "signatures" to encoder.json.encodeToJsonElement(key.signatures)
                            )
                        )
                    is Key.UnknownKey -> "${key.algorithm}:${key.keyId}" to key.raw
                }
            })
        )
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("KeysSerializer")
}