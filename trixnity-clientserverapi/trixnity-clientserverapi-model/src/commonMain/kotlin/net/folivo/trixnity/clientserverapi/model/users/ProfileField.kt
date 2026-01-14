package net.folivo.trixnity.clientserverapi.model.users

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = ProfileField.Serializer::class)
sealed interface ProfileField {
    @Serializable(with = Key.Serializer::class)
    interface Key<T : ProfileField> {
        val name: String

        object Serializer : KSerializer<Key<*>> {
            override val descriptor: SerialDescriptor =
                PrimitiveSerialDescriptor("ProfileField.Key", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): Key<*> =
                when (val name = decoder.decodeString()) {
                    DisplayName.name -> DisplayName.Key
                    AvatarUrl.name -> AvatarUrl.Key
                    TimeZone.name -> TimeZone.Key
                    else -> Unknown.Key(name)
                }

            override fun serialize(encoder: Encoder, value: Key<*>) {
                encoder.encodeString(value.name)
            }
        }
    }

    val key: Key<*>

    @Serializable
    data class DisplayName(
        @SerialName("displayname")
        val value: String? = null
    ) : ProfileField {
        @Transient
        override val key = Key

        companion object Key : ProfileField.Key<DisplayName> {
            override val name = "displayname"

            override fun toString(): String = name
        }
    }

    @Serializable
    data class AvatarUrl(
        @SerialName("avatar_url")
        val value: String? = null
    ) : ProfileField {
        @Transient
        override val key = Key

        companion object Key : ProfileField.Key<AvatarUrl> {
            override val name = "avatar_url"

            override fun toString(): String = name
        }
    }

    @Serializable
    data class TimeZone(
        @SerialName("m.tz")
        val value: String? = null
    ) : ProfileField {
        @Transient
        override val key = Key

        companion object Key : ProfileField.Key<AvatarUrl> {
            override val name = "m.tz"

            override fun toString(): String = name
        }
    }

    data class Unknown(override val key: Key, val raw: JsonElement) : ProfileField {
        constructor(key: String, raw: JsonElement) : this(Key(key), raw)

        data class Key(override val name: String) : ProfileField.Key<Unknown>
    }

    object Serializer : KSerializer<ProfileField> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ProfileField", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): ProfileField {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            val value = jsonObject.entries.firstOrNull()
                ?: throw SerializationException("no key found in response for profile field")
            return when (value.key) {
                DisplayName.name -> decoder.json.decodeFromJsonElement<DisplayName>(jsonObject)
                AvatarUrl.name -> decoder.json.decodeFromJsonElement<AvatarUrl>(jsonObject)
                TimeZone.name -> decoder.json.decodeFromJsonElement<TimeZone>(jsonObject)
                else -> Unknown(value.key, value.value)
            }
        }

        override fun serialize(encoder: Encoder, value: ProfileField) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(
                when (value) {
                    is DisplayName -> encoder.json.encodeToJsonElement(value)
                    is AvatarUrl -> encoder.json.encodeToJsonElement(value)
                    is TimeZone -> encoder.json.encodeToJsonElement(value)
                    is Unknown -> JsonObject(mapOf(value.key.name to value.raw))
                }
            )
        }
    }
}